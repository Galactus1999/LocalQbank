import Foundation
import SQLite3

/// Reads QBank questions from a SQLite database without modifying it.
/// The importer discovers candidate tables by column names and fails closed if the
/// database does not expose a recognizable question/options/answer schema.
enum SQLiteQBankImporter {
    private static let maximumQuestions = 100_000
    private static let maximumFieldBytes = 2 * 1024 * 1024

    static func importQuestions(from url: URL) throws -> [QBankQuestion] {
        var database: OpaquePointer?
        let result = sqlite3_open_v2(url.path, &database, SQLITE_OPEN_READONLY | SQLITE_OPEN_NOMUTEX, nil)
        guard result == SQLITE_OK, let database else {
            let message = database.map { String(cString: sqlite3_errmsg($0)) } ?? "SQLite could not open the selected file."
            if let database { sqlite3_close(database) }
            throw SQLiteQBankImportError.openFailed(message)
        }
        defer { sqlite3_close(database) }

        let tables = try tableNames(database)
        var candidateTables: [(name: String, columns: [String: String])] = []
        for table in tables {
            let columns = try columnNames(database, table: table)
            if hasQuestionSchema(columns) {
                candidateTables.append((table, columns))
            }
        }
        guard !candidateTables.isEmpty else {
            throw SQLiteQBankImportError.unsupportedSchema
        }

        var imported: [QBankQuestion] = []
        for candidate in candidateTables {
            let rows = try readQuestions(database, table: candidate.name, columns: candidate.columns)
            imported.append(contentsOf: rows)
            guard imported.count <= maximumQuestions else {
                throw SQLiteQBankImportError.tooManyQuestions(maximumQuestions)
            }
        }
        guard !imported.isEmpty else { throw SQLiteQBankImportError.noQuestions }
        return imported
    }

    private static func tableNames(_ database: OpaquePointer) throws -> [String] {
        let sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK, let statement else {
            throw SQLiteQBankImportError.queryFailed(String(cString: sqlite3_errmsg(database)))
        }
        defer { sqlite3_finalize(statement) }
        var names: [String] = []
        while sqlite3_step(statement) == SQLITE_ROW {
            if let value = sqlite3_column_text(statement, 0) {
                names.append(String(cString: value))
            }
        }
        return names
    }

    private static func columnNames(_ database: OpaquePointer, table: String) throws -> [String: String] {
        let escaped = table.replacingOccurrences(of: "\"", with: "\"\"")
        let sql = "PRAGMA table_info(\"\(escaped)\")"
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK, let statement else {
            throw SQLiteQBankImportError.queryFailed(String(cString: sqlite3_errmsg(database)))
        }
        defer { sqlite3_finalize(statement) }
        var result: [String: String] = [:]
        while sqlite3_step(statement) == SQLITE_ROW {
            guard let rawName = sqlite3_column_text(statement, 1) else { continue }
            let original = String(cString: rawName)
            result[normalize(original)] = original
        }
        return result
    }

    private static func hasQuestionSchema(_ columns: [String: String]) -> Bool {
        let stem = firstColumn(in: columns, aliases: ["question", "questiontext", "stem", "text", "prompt"])
        let options = firstColumn(in: columns, aliases: ["options", "choices", "answers", "optionsjson", "choicesjson"])
        let answer = firstColumn(in: columns, aliases: ["correctanswerindex", "answerindex", "correctanswer", "correctoption", "answer", "correct"])
        let individualOptions = ["optiona", "optionb", "optionc", "optiond"].filter { columns[$0] != nil }
        return stem != nil && answer != nil && (options != nil || individualOptions.count >= 2)
    }

    private static func readQuestions(
        _ database: OpaquePointer,
        table: String,
        columns: [String: String]
    ) throws -> [QBankQuestion] {
        let escapedTable = table.replacingOccurrences(of: "\"", with: "\"\"")
        let sql = "SELECT * FROM \"\(escapedTable)\" LIMIT \(maximumQuestions + 1)"
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK, let statement else {
            throw SQLiteQBankImportError.queryFailed(String(cString: sqlite3_errmsg(database)))
        }
        defer { sqlite3_finalize(statement) }

        let count = sqlite3_column_count(statement)
        var rowColumnNames: [String: Int32] = [:]
        for index in 0..<count {
            guard let rawName = sqlite3_column_name(statement, index) else { continue }
            rowColumnNames[normalize(String(cString: rawName))] = index
        }

        guard let stemIndex = index(for: ["question", "questiontext", "stem", "text", "prompt"], in: rowColumnNames),
              let answerIndex = index(for: ["correctanswerindex", "answerindex", "correctanswer", "correctoption", "answer", "correct"], in: rowColumnNames) else {
            throw SQLiteQBankImportError.unsupportedSchema
        }
        let optionsIndex = index(for: ["options", "choices", "answers", "optionsjson", "choicesjson"], in: rowColumnNames)
        let explanationIndex = index(for: ["explanation", "rationale", "solution", "description"], in: rowColumnNames)
        let optionIndexes = ["optiona", "optionb", "optionc", "optiond"].compactMap { key -> Int32? in
            rowColumnNames[key]
        }

        var questions: [QBankQuestion] = []
        while true {
            let step = sqlite3_step(statement)
            if step == SQLITE_DONE { break }
            guard step == SQLITE_ROW else {
                throw SQLiteQBankImportError.queryFailed(String(cString: sqlite3_errmsg(database)))
            }
            if questions.count >= maximumQuestions {
                throw SQLiteQBankImportError.tooManyQuestions(maximumQuestions)
            }

            let stem = try requiredText(statement, column: stemIndex, row: questions.count + 1, field: "question")
            let optionValues: [String]
            if let optionsIndex, let rawOptions = try optionalText(statement, column: optionsIndex, field: "options") {
                optionValues = try decodeOptions(rawOptions, row: questions.count + 1)
            } else {
                optionValues = try optionIndexes.compactMap { try optionalText(statement, column: $0, field: "option") }
            }
            guard !stem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, optionValues.count >= 2 else {
                throw SQLiteQBankImportError.invalidRow(questions.count + 1, "question text or options are missing")
            }

            let rawAnswer = try requiredText(statement, column: answerIndex, row: questions.count + 1, field: "answer")
            let answer = try decodeAnswer(rawAnswer, options: optionValues, row: questions.count + 1)
            let explanation = try explanationIndex.flatMap { try optionalText(statement, column: $0, field: "explanation") } ?? ""
            questions.append(QBankQuestion(question: stem, options: optionValues, correctAnswerIndex: answer, explanation: explanation))
        }
        return questions
    }

    private static func decodeOptions(_ raw: String, row: Int) throws -> [String] {
        guard let data = raw.data(using: .utf8) else {
            throw SQLiteQBankImportError.invalidRow(row, "options are not valid UTF-8")
        }
        if let values = try? JSONSerialization.jsonObject(with: data) as? [String] {
            return values
        }
        if let values = try? JSONSerialization.jsonObject(with: data) as? [String: String] {
            let keys = values.keys.sorted {
                optionRank($0) == optionRank($1)
                    ? $0.localizedStandardCompare($1) == .orderedAscending
                    : optionRank($0) < optionRank($1)
            }
            return keys.compactMap { values[$0] }
        }
        throw SQLiteQBankImportError.invalidRow(row, "options must be a JSON string array or keyed string object")
    }

    private static func decodeAnswer(_ raw: String, options: [String], row: Int) throws -> Int {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if let index = Int(value), options.indices.contains(index) { return index }
        if value.count == 1, let scalar = value.uppercased().unicodeScalars.first,
           scalar.value >= 65, scalar.value <= 68 {
            let index = Int(scalar.value - 65)
            guard options.indices.contains(index) else {
                throw SQLiteQBankImportError.invalidRow(row, "answer label points to a missing option")
            }
            return index
        }
        if let index = options.firstIndex(where: {
            $0.trimmingCharacters(in: .whitespacesAndNewlines)
                .localizedCaseInsensitiveCompare(value) == .orderedSame
        }) { return index }
        throw SQLiteQBankImportError.invalidRow(row, "answer does not match an option or valid zero-based index")
    }

    private static func requiredText(_ statement: OpaquePointer, column: Int32, row: Int, field: String) throws -> String {
        guard let value = try optionalText(statement, column: column, field: field),
              !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw SQLiteQBankImportError.invalidRow(row, "\(field) is empty")
        }
        return value
    }

    private static func optionalText(_ statement: OpaquePointer, column: Int32, field: String) throws -> String? {
        guard sqlite3_column_type(statement, column) != SQLITE_NULL else { return nil }
        let byteCount = Int(sqlite3_column_bytes(statement, column))
        guard byteCount <= maximumFieldBytes else {
            throw SQLiteQBankImportError.fieldTooLarge(field, maximumFieldBytes)
        }
        guard let value = sqlite3_column_text(statement, column) else { return nil }
        return String(decoding: UnsafeBufferPointer(start: value, count: byteCount), as: UTF8.self)
    }

    private static func index(for aliases: [String], in columns: [String: Int32]) -> Int32? {
        for alias in aliases {
            if let value = columns[alias] { return value }
        }
        return nil
    }

    private static func firstColumn(in columns: [String: String], aliases: [String]) -> String? {
        aliases.first(where: { columns[$0] != nil }).flatMap { columns[$0] }
    }

    private static func normalize(_ value: String) -> String {
        value.lowercased().filter { $0.isLetter || $0.isNumber }
    }

    private static func optionRank(_ value: String) -> Int {
        let key = value.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if key.count == 1, let scalar = key.unicodeScalars.first, scalar.value >= 65, scalar.value <= 90 {
            return Int(scalar.value - 65)
        }
        if let index = Int(key) { return index }
        return Int.max
    }
}

enum SQLiteQBankImportError: LocalizedError {
    case openFailed(String)
    case queryFailed(String)
    case unsupportedSchema
    case noQuestions
    case tooManyQuestions(Int)
    case fieldTooLarge(String, Int)
    case invalidRow(Int, String)

    var errorDescription: String? {
        switch self {
        case .openFailed(let message):
            return "Could not open this SQLite database read-only: \(message)"
        case .queryFailed(let message):
            return "Could not safely read the SQLite database: \(message)"
        case .unsupportedSchema:
            return "This database does not expose a recognized QBank question, options, and answer schema. No data was imported."
        case .noQuestions:
            return "No valid QBank questions were found in the selected database."
        case .tooManyQuestions(let limit):
            return "This database exceeds the safe import limit of \(limit) questions."
        case .fieldTooLarge(let field, let limit):
            return "The \(field) field exceeds the safe size limit of \(limit) bytes."
        case .invalidRow(let row, let reason):
            return "SQLite QBank import stopped at row \(row): \(reason). No questions were imported."
        }
    }
}
