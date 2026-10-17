import Foundation
import SQLite3
import ZIPFoundation

/// Imports the text side of an Anki .apkg archive into Rovex flashcards.
/// Media is not copied into the app yet; references are retained as readable placeholders.
enum APKGImporter {
    private static let maximumDatabaseBytes: UInt64 = 512 * 1024 * 1024
    private static let maximumNotes = 50_000
    private static let maximumFieldBytes = 2 * 1024 * 1024

    static func importFlashcards(from archiveURL: URL) throws -> [StudyFlashcard] {
        guard archiveURL.pathExtension.lowercased() == "apkg" else {
            throw APKGImportError.invalidArchive("Choose an Anki .apkg file.")
        }
        guard let archive = Archive(url: archiveURL, accessMode: .read) else {
            throw APKGImportError.invalidArchive("The selected file is not a readable ZIP/APKG archive.")
        }
        guard let databaseEntry = archive["collection.anki21"] ?? archive["collection.anki2"] else {
            throw APKGImportError.missingCollection
        }
        guard databaseEntry.uncompressedSize > 0,
              databaseEntry.uncompressedSize <= maximumDatabaseBytes else {
            throw APKGImportError.archiveLimit
        }

        let temporaryDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("rovex-apkg-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: temporaryDirectory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: temporaryDirectory) }

        let databaseURL = temporaryDirectory.appendingPathComponent("collection.sqlite")
        try archive.extract(databaseEntry, to: databaseURL)
        return try readFlashcards(from: databaseURL)
    }

    private static func readFlashcards(from url: URL) throws -> [StudyFlashcard] {
        var database: OpaquePointer?
        let result = sqlite3_open_v2(url.path, &database, SQLITE_OPEN_READONLY | SQLITE_OPEN_NOMUTEX, nil)
        guard result == SQLITE_OK, let database else {
            let message = database.map { String(cString: sqlite3_errmsg($0)) } ?? "SQLite could not open the Anki collection."
            if let database { sqlite3_close(database) }
            throw APKGImportError.database(message)
        }
        defer { sqlite3_close(database) }

        let modelsJSON = try querySingleText(database, sql: "SELECT models FROM col LIMIT 1")
        guard let modelData = modelsJSON.data(using: .utf8),
              let rawModels = try? JSONSerialization.jsonObject(with: modelData) as? [String: Any] else {
            throw APKGImportError.unsupportedCollection
        }
        var models: [String: NoteModel] = [:]
        for (key, value) in rawModels {
            guard let object = value as? [String: Any] else { continue }
            let fields = (object["flds"] as? [[String: Any]] ?? [])
                .sorted { ($0["ord"] as? Int ?? 0) < ($1["ord"] as? Int ?? 0) }
                .compactMap { $0["name"] as? String }
            let template = (object["tmpls"] as? [[String: Any]])?.first
            models[key] = NoteModel(
                fields: fields,
                questionTemplate: template?["qfmt"] as? String ?? "",
                answerTemplate: template?["afmt"] as? String ?? ""
            )
        }

        let sql = """
        SELECT DISTINCT n.mid, n.flds
        FROM notes AS n
        INNER JOIN cards AS c ON c.nid = n.id
        ORDER BY n.id
        LIMIT \(maximumNotes + 1)
        """
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK, let statement else {
            throw APKGImportError.unsupportedCollection
        }
        defer { sqlite3_finalize(statement) }

        var cards: [StudyFlashcard] = []
        while true {
            let step = sqlite3_step(statement)
            if step == SQLITE_DONE { break }
            guard step == SQLITE_ROW else {
                throw APKGImportError.database(String(cString: sqlite3_errmsg(database)))
            }
            guard cards.count < maximumNotes else { throw APKGImportError.tooManyNotes(maximumNotes) }
            let modelID = String(sqlite3_column_int64(statement, 0))
            guard let rawFields = sqlite3_column_text(statement, 1) else {
                throw APKGImportError.invalidNote(cards.count + 1)
            }
            let fieldValues = String(cString: rawFields).components(separatedBy: "\u{1F}")
            guard fieldValues.allSatisfy({ $0.utf8.count <= maximumFieldBytes }) else {
                throw APKGImportError.fieldTooLarge(cards.count + 1)
            }
            let model = models[modelID]
            let frontIndex = fieldIndex(
                referencedBy: model?.questionTemplate ?? "",
                in: model?.fields ?? [],
                fieldCount: fieldValues.count
            ) ?? 0
            let front = fieldValues.indices.contains(frontIndex) ? readableText(fieldValues[frontIndex]) : ""
            let answerIndexes = fieldIndexes(
                referencedBy: model?.answerTemplate ?? "",
                in: model?.fields ?? []
            ).filter { $0 != frontIndex && fieldValues.indices.contains($0) }
            let selectedBackIndexes = answerIndexes.isEmpty
                ? fieldValues.indices.filter { $0 != frontIndex }
                : answerIndexes
            let back = selectedBackIndexes.map { readableText(fieldValues[$0]) }
                .filter { !$0.isEmpty }
                .joined(separator: "\n\n")
            guard !front.isEmpty, !back.isEmpty else {
                // Some note types contain metadata-only notes or cards with empty sides.
                continue
            }
            cards.append(StudyFlashcard(front: front, back: back))
        }
        guard !cards.isEmpty else { throw APKGImportError.noCards }
        return cards
    }

    private static func querySingleText(_ database: OpaquePointer, sql: String) throws -> String {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK, let statement else {
            throw APKGImportError.unsupportedCollection
        }
        defer { sqlite3_finalize(statement) }
        guard sqlite3_step(statement) == SQLITE_ROW,
              let raw = sqlite3_column_text(statement, 0) else {
            throw APKGImportError.unsupportedCollection
        }
        return String(cString: raw)
    }

    private static func fieldIndexes(referencedBy template: String, in fields: [String]) -> [Int] {
        let names = templateFieldNames(template)
        return names.compactMap { name in
            fields.firstIndex { $0.caseInsensitiveCompare(name) == .orderedSame }
        }.reduce(into: [Int]()) { result, index in
            if !result.contains(index) { result.append(index) }
        }
    }

    private static func fieldIndex(referencedBy template: String, in fields: [String], fieldCount: Int) -> Int? {
        fieldIndexes(referencedBy: template, in: fields).first ?? (fieldCount > 0 ? 0 : nil)
    }

    private static func templateFieldNames(_ template: String) -> [String] {
        guard let regex = try? NSRegularExpression(pattern: #"\\{\\{(?:[^}:]+:)?([^}]+)\\}\\}"#) else { return [] }
        let range = NSRange(template.startIndex..., in: template)
        return regex.matches(in: template, range: range).compactMap { match in
            guard let range = Range(match.range(at: 1), in: template) else { return nil }
            return String(template[range]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }

    private static func readableText(_ html: String) -> String {
        var value = html
        value = value.replacingOccurrences(
            of: #"(?i)<img\\b[^>]*?src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>"#,
            with: "[image: $1]",
            options: .regularExpression
        )
        value = value.replacingOccurrences(
            of: #"(?i)<audio\\b[^>]*?src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>.*?</audio>"#,
            with: "[audio: $1]",
            options: .regularExpression
        )
        value = value.replacingOccurrences(of: #"(?i)<br\\s*/?>|</(?:div|p|li|tr|h[1-6])\\s*>"#, with: "\n", options: .regularExpression)
        value = value.replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
        let entities = [
            "&nbsp;": " ", "&amp;": "&", "&lt;": "<", "&gt;": ">",
            "&quot;": "\"", "&#39;": "'", "&apos;": "'"
        ]
        for (entity, replacement) in entities {
            value = value.replacingOccurrences(of: entity, with: replacement, options: .caseInsensitive)
        }
        return value
            .replacingOccurrences(of: "\r\n", with: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private struct NoteModel {
        let fields: [String]
        let questionTemplate: String
        let answerTemplate: String
    }
}

enum APKGImportError: LocalizedError {
    case invalidArchive(String)
    case missingCollection
    case archiveLimit
    case database(String)
    case unsupportedCollection
    case tooManyNotes(Int)
    case fieldTooLarge(Int)
    case invalidNote(Int)
    case noCards

    var errorDescription: String? {
        switch self {
        case .invalidArchive(let message): return message
        case .missingCollection: return "This APKG file does not contain a supported collection.anki2 or collection.anki21 database."
        case .archiveLimit: return "The Anki collection database is empty or exceeds the 512 MB import limit."
        case .database(let message): return "Could not read the Anki collection: \(message)"
        case .unsupportedCollection: return "This Anki collection schema is unsupported or incomplete."
        case .tooManyNotes(let limit): return "This deck exceeds the safe import limit of \(limit) notes."
        case .fieldTooLarge(let row): return "Anki note \(row) contains a field larger than the safe import limit."
        case .invalidNote(let row): return "Anki note \(row) has invalid field data."
        case .noCards: return "No usable text flashcards were found in this APKG file."
        }
    }
}
