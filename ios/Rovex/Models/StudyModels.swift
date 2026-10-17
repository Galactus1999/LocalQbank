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
