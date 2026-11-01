import Foundation

struct QBankQuestion: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var question: String
    var options: [String]
    var correctAnswerIndex: Int
    var explanation: String

    var hasValidAnswer: Bool {
        options.indices.contains(correctAnswerIndex)
    }

    private enum CodingKeys: String, CodingKey {
        case id, question, options, correctAnswerIndex, explanation
    }

    init(id: UUID = UUID(), question: String, options: [String], correctAnswerIndex: Int, explanation: String = "") {
        self.id = id
        self.question = question
        self.options = options
        self.correctAnswerIndex = correctAnswerIndex
        self.explanation = explanation
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        question = try container.decode(String.self, forKey: .question)
        options = try container.decode([String].self, forKey: .options)
        correctAnswerIndex = try container.decode(Int.self, forKey: .correctAnswerIndex)
        explanation = try container.decodeIfPresent(String.self, forKey: .explanation) ?? ""
    }
}

struct StudyFlashcard: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var front: String
    var back: String
    var createdAt: Date = Date()
}

struct QBankImportDocument: Decodable {
    let questions: [QBankQuestion]

    init(from decoder: Decoder) throws {
        if let array = try? [QBankQuestion](from: decoder) {
            questions = array
            return
        }
        let container = try decoder.container(keyedBy: CodingKeys.self)
        questions = try container.decode([QBankQuestion].self, forKey: .questions)
    }

    private enum CodingKeys: String, CodingKey {
        case questions
    }
}
