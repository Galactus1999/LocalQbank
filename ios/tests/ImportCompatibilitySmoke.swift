import Foundation

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

        print("PASS: canonical JSON, wrapper aliases, option-object ordering, optionA-D fields, answer mapping, invalid-answer rejection, and archive round-trip")
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
