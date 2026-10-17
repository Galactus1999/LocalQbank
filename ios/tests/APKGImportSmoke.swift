import Foundation
import SQLite3
import ZIPFoundation

@main
struct APKGImportSmoke {
    static func main() throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("rovex-apkg-smoke-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }

        let databaseURL = root.appendingPathComponent("collection.anki2")
        var database: OpaquePointer?
        guard sqlite3_open(databaseURL.path, &database) == SQLITE_OK, let db = database else {
            throw SmokeFailure.failed("open APKG fixture database")
        }
        let sql = """
        CREATE TABLE col (models TEXT NOT NULL);
        INSERT INTO col(models) VALUES ('{"100":{"flds":[{"name":"Front","ord":0},{"name":"Back","ord":1}],"tmpls":[{"qfmt":"{{Front}}","afmt":"{{FrontSide}}<hr id=answer>{{Back}}"}]}}');
        CREATE TABLE notes (id INTEGER PRIMARY KEY, mid INTEGER NOT NULL, flds TEXT NOT NULL);
        CREATE TABLE cards (id INTEGER PRIMARY KEY, nid INTEGER NOT NULL);
        INSERT INTO notes(id, mid, flds) VALUES
          (1, 100, 'Question <img src="test.png">' || char(31) || '<b>Answer &amp; explanation</b>'),
          (2, 100, 'Second question' || char(31) || 'Second answer');
        INSERT INTO cards(id, nid) VALUES (1, 1), (2, 2);
        """
        let sqlResult = sqlite3_exec(db, sql, nil, nil, nil)
        sqlite3_close(db)
        guard sqlResult == SQLITE_OK else { throw SmokeFailure.failed("populate APKG fixture database") }

        let archiveURL = root.appendingPathComponent("sample.apkg")
        guard let archive = Archive(url: archiveURL, accessMode: .create) else {
            throw SmokeFailure.failed("create APKG ZIP archive")
        }
        try archive.addEntry(with: "collection.anki2", relativeTo: root, compressionMethod: .deflate)

        let cards = try APKGImporter.importFlashcards(from: archiveURL)
        try require(cards.count == 2, "import two distinct Anki notes")
        try require(cards[0].front.contains("Question") && cards[0].front.contains("[image: test.png]"), "preserve image reference as placeholder")
        try require(cards[0].back == "Answer & explanation", "strip basic HTML and decode entities")
        try require(cards[1].front == "Second question" && cards[1].back == "Second answer", "map Anki template fields to front/back")
        print("PASS: APKG ZIP extraction, Anki collection/models/notes/cards parsing, template-based front/back mapping, HTML cleanup, and media placeholder")
    }

    private static func require(_ condition: Bool, _ name: String) throws {
        if !condition { throw SmokeFailure.failed(name) }
    }
}

enum SmokeFailure: Error, CustomStringConvertible {
    case failed(String)
    var description: String {
        switch self {
        case .failed(let name): return "APKG smoke test failed: \(name)"
        }
    }
}
