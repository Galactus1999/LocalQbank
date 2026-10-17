import Foundation
import Combine

@MainActor
final class LocalStudyStore: ObservableObject {
    static let shared = LocalStudyStore()

    @Published private(set) var questions: [QBankQuestion] = []
    @Published private(set) var flashcards: [StudyFlashcard] = []
    @Published private(set) var storageError: String?

    private let fileManager = FileManager.default
    private let encoder: JSONEncoder
    private let decoder = JSONDecoder()
    private let directoryURL: URL
    private let archiveFileName = "study-data.json"

    private init() {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        directoryURL = base.appendingPathComponent("Rovex", isDirectory: true)
        encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        reload()
    }

    func reload() {
        do {
            try fileManager.createDirectory(at: directoryURL, withIntermediateDirectories: true, attributes: nil)
            let archiveURL = directoryURL.appendingPathComponent(archiveFileName)
            if fileManager.fileExists(atPath: archiveURL.path) {
                let archive = try decoder.decode(StudyDataArchive.self, from: Data(contentsOf: archiveURL))
                guard archive.schemaVersion == 1 else {
                    throw StudyStoreError.unsupportedArchiveVersion(archive.schemaVersion)
                }
                questions = archive.questions
                flashcards = archive.flashcards
            } else {
                // One-time migration from the first iOS foundation's two-file layout.
                questions = try readLegacy([QBankQuestion].self, from: "questions.json")
                flashcards = try readLegacy([StudyFlashcard].self, from: "flashcards.json")
                try persistState(questions: questions, flashcards: flashcards)
            }
            storageError = nil
        } catch {
            storageError = "Local study data could not be read: \(error.localizedDescription)"
        }
    }

    @discardableResult
    func importQuestions(from data: Data) throws -> Int {
        let document = try decoder.decode(QBankImportDocument.self, from: data)
        return try merge(questions: document.questions, flashcards: [])
    }

    @discardableResult
    func importData(from data: Data) throws -> (questionsAdded: Int, flashcardsAdded: Int) {
        if let archive = try? decoder.decode(StudyDataArchive.self, from: data) {
            guard archive.schemaVersion == 1 else {
                throw StudyStoreError.unsupportedArchiveVersion(archive.schemaVersion)
            }
            guard !archive.questions.isEmpty || !archive.flashcards.isEmpty else {
                throw StudyStoreError.emptyImport
            }
            let questionCount = try merge(questions: archive.questions, flashcards: archive.flashcards)
            return questionCount
        }

        return (try importQuestions(from: data), 0)
    }

    func exportData() throws -> Data {
        try encoder.encode(StudyDataArchive(questions: questions, flashcards: flashcards))
    }

    func addFlashcard(front: String, back: String) throws {
        let cleanFront = front.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanBack = back.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanFront.isEmpty, !cleanBack.isEmpty else {
            throw StudyStoreError.emptyFlashcard
        }
        let updatedFlashcards = [StudyFlashcard(front: cleanFront, back: cleanBack)] + flashcards
        try persistState(questions: questions, flashcards: updatedFlashcards)
        flashcards = updatedFlashcards
    }

    func deleteFlashcard(id: UUID) throws {
        let updatedFlashcards = flashcards.filter { $0.id != id }
        try persistState(questions: questions, flashcards: updatedFlashcards)
        flashcards = updatedFlashcards
    }

    private func merge(questions incomingQuestions: [QBankQuestion], flashcards incomingFlashcards: [StudyFlashcard]) throws -> (questionsAdded: Int, flashcardsAdded: Int) {
        guard incomingQuestions.allSatisfy({
            !$0.question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                && $0.options.count >= 2
                && $0.hasValidAnswer
        }) else {
            if !incomingQuestions.isEmpty { throw StudyStoreError.invalidQuestion }
            throw StudyStoreError.emptyImport
        }
        guard incomingFlashcards.allSatisfy({
            !$0.front.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                && !$0.back.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }) else {
            throw StudyStoreError.invalidFlashcard
        }

        var seenQuestions = Set(questions.map { normalized($0.question) })
        let acceptedQuestions = incomingQuestions.filter { seenQuestions.insert(normalized($0.question)).inserted }
        // Imported IDs are untrusted and may collide with existing records; assign fresh local IDs.
        let newQuestions = acceptedQuestions.map { item -> QBankQuestion in
            var localItem = item
            localItem.id = UUID()
            return localItem
        }

        var seenFlashcards = Set(flashcards.map { flashcardKey($0.front, $0.back) })
        let acceptedFlashcards = incomingFlashcards.filter {
            seenFlashcards.insert(flashcardKey($0.front, $0.back)).inserted
        }
        let newFlashcards = acceptedFlashcards.map { item -> StudyFlashcard in
            var localItem = item
            localItem.id = UUID()
            return localItem
        }

        let updatedQuestions = questions + newQuestions
        let updatedFlashcards = newFlashcards + flashcards
        guard !newQuestions.isEmpty || !newFlashcards.isEmpty else {
            return (0, 0)
        }

        // A single atomic archive write keeps questions and flashcards in one consistent snapshot.
        try persistState(questions: updatedQuestions, flashcards: updatedFlashcards)
        questions = updatedQuestions
        flashcards = updatedFlashcards
        return (newQuestions.count, newFlashcards.count)
    }

    private func flashcardKey(_ front: String, _ back: String) -> String {
        normalized(front) + "\u{1F}" + normalized(back)
    }

    private func normalized(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .split(whereSeparator: \.isWhitespace)
            .joined(separator: " ")
    }

    private func readLegacy<T: Decodable>(_ type: T.Type, from name: String) throws -> T {
        let url = directoryURL.appendingPathComponent(name)
        guard fileManager.fileExists(atPath: url.path) else {
            if type == [QBankQuestion].self { return [] as! T }
            if type == [StudyFlashcard].self { return [] as! T }
            throw StudyStoreError.unsupportedLegacyStore
        }
        return try decoder.decode(type, from: Data(contentsOf: url))
    }

    private func persistState(questions: [QBankQuestion], flashcards: [StudyFlashcard]) throws {
        try fileManager.createDirectory(at: directoryURL, withIntermediateDirectories: true, attributes: nil)
        let archive = StudyDataArchive(questions: questions, flashcards: flashcards)
        let data = try encoder.encode(archive)
        try data.write(to: directoryURL.appendingPathComponent(archiveFileName), options: .atomic)
        storageError = nil
    }
}

enum StudyStoreError: LocalizedError {
    case emptyImport
    case invalidQuestion
    case emptyFlashcard
    case invalidFlashcard
    case unsupportedArchiveVersion(Int)
    case unsupportedLegacyStore

    var errorDescription: String? {
        switch self {
        case .emptyImport:
            return "The selected file contains no questions or flashcards."
        case .invalidQuestion:
            return "Each question needs text, at least two options, and a valid correct-answer index."
        case .emptyFlashcard:
            return "Enter both the front and back of the flashcard."
        case .invalidFlashcard:
            return "Each imported flashcard needs both a front and a back."
        case .unsupportedArchiveVersion(let version):
            return "Study archive version \(version) is not supported by this app version."
        case .unsupportedLegacyStore:
            return "This legacy local data format is not supported."
        }
    }
}
