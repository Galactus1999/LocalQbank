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
        case id, question, questionText, stem, text
        case options, optionA, optionB, optionC, optionD
        case correctAnswerIndex, answerIndex, correctAnswer, correctOption, answer
        case explanation, rationale
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

        // Android/database identifiers are not necessarily UUIDs. Never reject an otherwise
        // usable question solely because an external ID uses another format.
        id = (try? container.decode(UUID.self, forKey: .id)) ?? UUID()
        question = try Self.decodeFirstString(
            container,
            keys: [.question, .questionText, .stem, .text]
        ) ?? ""

        if let array = try? container.decode([String].self, forKey: .options) {
            options = array
        } else if let keyedOptions = try? container.decode([String: String].self, forKey: .options) {
            options = keyedOptions.keys.sorted(by: Self.optionKeyOrder).compactMap { keyedOptions[$0] }
        } else {
            let optionKeys: [(CodingKeys, String)] = [
                (.optionA, "A"), (.optionB, "B"), (.optionC, "C"), (.optionD, "D")
            ]
            options = optionKeys.compactMap { key, _ in
                try? container.decode(String.self, forKey: key)
            }
        }

        if let index = try? container.decode(Int.self, forKey: .correctAnswerIndex) {
            correctAnswerIndex = index
        } else if let index = try? container.decode(Int.self, forKey: .answerIndex) {
            correctAnswerIndex = index
        } else {
            let answerText = Self.decodeFirstString(
                container,
                keys: [.correctAnswer, .correctOption, .answer]
            )?.trimmingCharacters(in: .whitespacesAndNewlines)

            if let answerText, let index = Int(answerText) {
                // Integer values follow the documented zero-based JSON contract.
                correctAnswerIndex = index
            } else if let answerText, answerText.count == 1,
                      let scalar = answerText.uppercased().unicodeScalars.first,
                      ("A"..."D").contains(String(scalar)) {
                correctAnswerIndex = Int(scalar.value - UnicodeScalar("A").value)
            } else if let answerText,
                      let index = options.firstIndex(where: {
                          $0.trimmingCharacters(in: .whitespacesAndNewlines)
                              .localizedCaseInsensitiveCompare(answerText) == .orderedSame
                      }) {
                correctAnswerIndex = index
            } else {
                correctAnswerIndex = -1
            }
        }

        explanation = Self.decodeFirstString(container, keys: [.explanation, .rationale]) ?? ""
    }

    private static func decodeFirstString(
        _ container: KeyedDecodingContainer<CodingKeys>,
        keys: [CodingKeys]
    ) -> String? {
        for key in keys {
            if let value = try? container.decode(String.self, forKey: key) {
                return value
            }
        }
        return nil
    }

    private static func optionKeyOrder(_ lhs: String, _ rhs: String) -> Bool {
        func rank(_ key: String) -> Int {
            let normalized = key.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if normalized.count == 1, let scalar = normalized.unicodeScalars.first,
               ("A"..."Z").contains(String(scalar)) {
                return Int(scalar.value - UnicodeScalar("A").value)
            }
            if let number = Int(normalized) { return number }
            return Int.max
        }
        let leftRank = rank(lhs)
        let rightRank = rank(rhs)
        return leftRank == rightRank ? lhs.localizedStandardCompare(rhs) == .orderedAscending : leftRank < rightRank
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
        id = (try? container.decode(UUID.self, forKey: .id)) ?? UUID()
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
        for key in [CodingKeys.questions, .items, .data, .results] {
            if let value = try? container.decode([QBankQuestion].self, forKey: key) {
                questions = value
                return
            }
        }
        throw DecodingError.keyNotFound(
            CodingKeys.questions,
            DecodingError.Context(codingPath: decoder.codingPath, debugDescription: "Expected a question array or an object containing questions/items/data/results.")
        )
    }

    private enum CodingKeys: String, CodingKey {
        case questions, items, data, results
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
