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
        CREATE TABLE keyed_question (question TEXT, options TEXT, answer TEXT);
        INSERT INTO keyed_question VALUES
          ('SQLite keyed options', '{"D":"Fourth","B":"Second","A":"First","C":"Third"}', 'C');
        CREATE TABLE option_fields (
            stem TEXT, optionA TEXT, optionB TEXT, optionC TEXT, optionD TEXT, answer TEXT
        );
        INSERT INTO option_fields VALUES ('SQLite option fields', 'One', 'Two', 'Three', 'Four', 'D');
        """
        try require(sqlite3_exec(openedDatabase, sql, nil, nil, nil) == SQLITE_OK, "populate SQLite fixture")
        sqlite3_close(openedDatabase)
        database = nil
        let databaseBytesBeforeImport = try Data(contentsOf: databaseURL)
        let sqliteQuestions = try SQLiteQBankImporter.importQuestions(from: databaseURL)
        let databaseBytesAfterImport = try Data(contentsOf: databaseURL)
        try require(databaseBytesBeforeImport == databaseBytesAfterImport, "SQLite source remains byte-identical after read-only import")
        try require(sqliteQuestions.count == 4, "SQLite question count across supported table layouts")
        let standardQuestion = sqliteQuestions.first { $0.question == "SQLite question one" }
        try require(standardQuestion?.correctAnswerIndex == 1, "SQLite letter answer mapping")
        try require(standardQuestion?.explanation == "SQLite explanation", "SQLite explanation")
        let sqliteNumericQuestion = sqliteQuestions.first { $0.question == "SQLite question two" }
        try require(sqliteNumericQuestion?.correctAnswerIndex == 1, "SQLite numeric answer mapping")
        let keyedQuestion = sqliteQuestions.first { $0.question == "SQLite keyed options" }
        try require(keyedQuestion?.options == ["First", "Second", "Third", "Fourth"], "SQLite keyed options order")
        try require(keyedQuestion?.correctAnswerIndex == 2, "SQLite keyed options answer mapping")
        let optionFieldQuestion = sqliteQuestions.first { $0.question == "SQLite option fields" }
        try require(optionFieldQuestion?.options == ["One", "Two", "Three", "Four"], "SQLite optionA-D column mapping")
        try require(optionFieldQuestion?.correctAnswerIndex == 3, "SQLite optionA-D answer mapping")

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

        let malformedURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("rovex-sqlite-malformed-\\(UUID().uuidString).db")
        defer { try? FileManager.default.removeItem(at: malformedURL) }
        var malformedDatabase: OpaquePointer?
        try require(sqlite3_open(malformedURL.path, &malformedDatabase) == SQLITE_OK, "create malformed SQLite fixture")
        guard let openedMalformedDatabase = malformedDatabase else { throw SmokeTestError.failed("malformed SQLite fixture handle") }
        defer { if let malformedDatabase { sqlite3_close(malformedDatabase) } }
        let malformedSQL = """
        CREATE TABLE questions (question TEXT, options TEXT, answer TEXT);
        INSERT INTO questions VALUES
          ('Valid row before malformed row', '["One","Two"]', 'A'),
          ('Malformed row', 'not-json', 'B');
        """
        try require(sqlite3_exec(openedMalformedDatabase, malformedSQL, nil, nil, nil) == SQLITE_OK, "populate malformed SQLite fixture")
        sqlite3_close(openedMalformedDatabase)
        malformedDatabase = nil
        do {
            _ = try SQLiteQBankImporter.importQuestions(from: malformedURL)
            throw SmokeTestError.failed("malformed SQLite row must reject the complete import")
        } catch SQLiteQBankImportError.invalidRow {
            // Expected: validation fails before the caller can merge any rows.
        }

        print("PASS: JSON/archive compatibility; SQLite read-only integrity, JSON/keyed/optionA-D layouts, answer mapping, unsupported schemas, and malformed-row rejection")
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
