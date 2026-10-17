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

enum FlashcardRating: String, CaseIterable, Identifiable {
    case again = "Again"
    case hard = "Hard"
    case good = "Good"
    case easy = "Easy"

    var id: String { rawValue }
}

struct StudyFlashcard: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var front: String
    var back: String
    var createdAt: Date = Date()
    var dueAt: Date = Date()
    var intervalDays: Int = 0
    var repetitions: Int = 0
    var easeFactor: Double = 2.5
    var lastReviewedAt: Date?

    private enum CodingKeys: String, CodingKey {
        case id, front, back, createdAt, dueAt, intervalDays, repetitions, easeFactor, lastReviewedAt
    }

    init(
        id: UUID = UUID(),
        front: String,
        back: String,
        createdAt: Date = Date(),
        dueAt: Date = Date(),
        intervalDays: Int = 0,
        repetitions: Int = 0,
        easeFactor: Double = 2.5,
        lastReviewedAt: Date? = nil
    ) {
        self.id = id
        self.front = front
        self.back = back
        self.createdAt = createdAt
        self.dueAt = dueAt
        self.intervalDays = intervalDays
        self.repetitions = repetitions
        self.easeFactor = easeFactor
        self.lastReviewedAt = lastReviewedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        front = try container.decode(String.self, forKey: .front)
        back = try container.decode(String.self, forKey: .back)
        createdAt = try container.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
        dueAt = try container.decodeIfPresent(Date.self, forKey: .dueAt) ?? Date()
        intervalDays = max(0, try container.decodeIfPresent(Int.self, forKey: .intervalDays) ?? 0)
        repetitions = max(0, try container.decodeIfPresent(Int.self, forKey: .repetitions) ?? 0)
        let decodedEase = try container.decodeIfPresent(Double.self, forKey: .easeFactor) ?? 2.5
        easeFactor = decodedEase.isFinite ? min(3.0, max(1.3, decodedEase)) : 2.5
        lastReviewedAt = try container.decodeIfPresent(Date.self, forKey: .lastReviewedAt)
    }

    var isDue: Bool { dueAt <= Date() }
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

struct StudyDataArchive: Codable {
    var schemaVersion: Int
    var exportedAt: Date
    var questions: [QBankQuestion]
    var flashcards: [StudyFlashcard]

    init(schemaVersion: Int = 1, exportedAt: Date = Date(), questions: [QBankQuestion], flashcards: [StudyFlashcard]) {
        self.schemaVersion = schemaVersion
        self.exportedAt = exportedAt
        self.questions = questions
        self.flashcards = flashcards
    }

    private enum CodingKeys: String, CodingKey {
        case schemaVersion, exportedAt, questions, flashcards
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try container.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? 1
        exportedAt = try container.decodeIfPresent(Date.self, forKey: .exportedAt) ?? Date()
        questions = try container.decodeIfPresent([QBankQuestion].self, forKey: .questions) ?? []
        flashcards = try container.decodeIfPresent([StudyFlashcard].self, forKey: .flashcards) ?? []
    }
}
