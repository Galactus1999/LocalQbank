import Foundation
import SQLite3

@main
struct ImportCompatibilitySmoke {
    static func main() throws {
        let decoder = JSONDecoder()

        let canonical = Data("""
        [
          {
            "question": "Canonical question",
            "options": ["A1", "B1", "C1", "D1"],
            "correctAnswerIndex": 2,
            "explanation": "Canonical explanation"
          }
        ]
        """.utf8)
        let canonicalDocument = try decoder.decode(QBankImportDocument.self, from: canonical)
        try require(canonicalDocument.questions.count == 1, "canonical array count")
        try require(canonicalDocument.questions[0].correctAnswerIndex == 2, "canonical zero-based answer")
        try require(canonicalDocument.questions[0].hasValidAnswer, "canonical answer validity")

        let androidStyle = Data("""
        {
          "items": [
            {
              "id": 92831,
              "questionText": "Alias question",
              "options": {"D": "Option D", "B": "Option B", "A": "Option A", "C": "Option C"},
              "correctAnswer": "C",
              "rationale": "Alias explanation"
            }
          ]
        }
        """.utf8)
        let aliasDocument = try decoder.decode(QBankImportDocument.self, from: androidStyle)
        let aliasQuestion = aliasDocument.questions[0]
        try require(aliasQuestion.question == "Alias question", "questionText alias")
        try require(aliasQuestion.options == ["Option A", "Option B", "Option C", "Option D"], "keyed options order")
        try require(aliasQuestion.correctAnswerIndex == 2, "letter answer mapping")
        try require(aliasQuestion.explanation == "Alias explanation", "rationale alias")
        try require(aliasQuestion.hasValidAnswer, "alias answer validity")

        let optionFields = Data("""
        {
          "data": [
            {
              "stem": "Option field question",
              "optionA": "One",
              "optionB": "Two",
              "optionC": "Three",
              "optionD": "Four",
              "answer": "D"
            }
          ]
        }
        """.utf8)
        let optionFieldDocument = try decoder.decode(QBankImportDocument.self, from: optionFields)
        try require(optionFieldDocument.questions[0].options == ["One", "Two", "Three", "Four"], "optionA-D aliases")
        try require(optionFieldDocument.questions[0].correctAnswerIndex == 3, "D answer mapping")

        let numericAlias = Data("""
        [{"question":"Numeric alias","options":["One","Two","Three"],"correctAnswer":1}]
        """.utf8)
        let numericQuestion = try decoder.decode(QBankImportDocument.self, from: numericAlias).questions[0]
        try require(numericQuestion.correctAnswerIndex == 1, "numeric correctAnswer alias remains zero-based")

        let invalidAnswer = Data("""
        [{"question":"No answer","options":["One","Two"],"correctAnswer":"Z"}]
        """.utf8)
        let invalidQuestion = try decoder.decode(QBankImportDocument.self, from: invalidAnswer).questions[0]
        try require(!invalidQuestion.hasValidAnswer, "invalid answer must remain invalid, not be guessed")

        let archive = StudyDataArchive(
            questions: [aliasQuestion],
            flashcards: [StudyFlashcard(front: "Front", back: "Back")]
        )
        let roundTrip = try decoder.decode(StudyDataArchive.self, from: JSONEncoder().encode(archive))
        try require(roundTrip.questions.count == 1 && roundTrip.flashcards.count == 1, "archive encode/decode round trip")

        let databaseURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("rovex-sqlite-import-\\(UUID().uuidString).db")
        defer { try? FileManager.default.removeItem(at: databaseURL) }
        var database: OpaquePointer?
        try require(sqlite3_open(databaseURL.path, &database) == SQLITE_OK, "create SQLite fixture")
        guard let openedDatabase = database else { throw SmokeTestError.failed("SQLite fixture handle") }
        defer { if let database { sqlite3_close(database) } }
        let sql = """
        CREATE TABLE question_bank (
            id INTEGER PRIMARY KEY,
            question_text TEXT NOT NULL,
            options_json TEXT NOT NULL,
            correct_answer TEXT NOT NULL,
            explanation TEXT
        );
        INSERT INTO question_bank(question_text, options_json, correct_answer, explanation)
        VALUES
          ('SQLite question one', '["First","Second","Third","Fourth"]', 'B', 'SQLite explanation'),
          ('SQLite question two', '["Alpha","Beta"]', '1', '');
        """
        try require(sqlite3_exec(openedDatabase, sql, nil, nil, nil) == SQLITE_OK, "populate SQLite fixture")
        sqlite3_close(openedDatabase)
        database = nil
        let sqliteQuestions = try SQLiteQBankImporter.importQuestions(from: databaseURL)
        try require(sqliteQuestions.count == 2, "SQLite question count")
        try require(sqliteQuestions[0].correctAnswerIndex == 1, "SQLite letter answer mapping")
        try require(sqliteQuestions[0].explanation == "SQLite explanation", "SQLite explanation")
        try require(sqliteQuestions[1].correctAnswerIndex == 1, "SQLite numeric answer mapping")

        let unsupportedURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("rovex-sqlite-unsupported-\\(UUID().uuidString).db")
        defer { try? FileManager.default.removeItem(at: unsupportedURL) }
        var unsupportedDatabase: OpaquePointer?
        try require(sqlite3_open(unsupportedURL.path, &unsupportedDatabase) == SQLITE_OK, "create unsupported SQLite fixture")
        guard let openedUnsupportedDatabase = unsupportedDatabase else { throw SmokeTestError.failed("unsupported SQLite fixture handle") }
        defer { if let unsupportedDatabase { sqlite3_close(unsupportedDatabase) } }
        try require(sqlite3_exec(openedUnsupportedDatabase, "CREATE TABLE metadata(key TEXT, value TEXT);", nil, nil, nil) == SQLITE_OK, "populate unsupported SQLite fixture")
        sqlite3_close(openedUnsupportedDatabase)
        unsupportedDatabase = nil
        do {
            _ = try SQLiteQBankImporter.importQuestions(from: unsupportedURL)
            throw SmokeTestError.failed("unsupported SQLite schema must be rejected")
        } catch SQLiteQBankImportError.unsupportedSchema {
            // Expected: no recognizable question/options/answer schema.
        }

        print("PASS: JSON compatibility and archive tests; read-only SQLite import, answer mapping, explanation, and unsupported-schema rejection")
    }

    private static func require(_ condition: Bool, _ name: String) throws {
        if !condition {
            throw SmokeTestError.failed(name)
        }
    }
}

enum SmokeTestError: Error, CustomStringConvertible {
    case failed(String)

    var description: String {
        switch self {
        case .failed(let name): return "QBank import smoke test failed: \(name)"
        }
    }
}
