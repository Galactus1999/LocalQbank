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
            questions = try read([QBankQuestion].self, defaultValue: [], from: "questions.json")
            flashcards = try read([StudyFlashcard].self, defaultValue: [], from: "flashcards.json")
            storageError = nil
        } catch {
            storageError = "Local study data could not be read: \(error.localizedDescription)"
        }
    }

    @discardableResult
    func importQuestions(from data: Data) throws -> Int {
        let document = try decoder.decode(QBankImportDocument.self, from: data)
        guard !document.questions.isEmpty else {
            throw StudyStoreError.emptyImport
        }
        guard document.questions.allSatisfy({
            !$0.question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                && $0.options.count >= 2
                && $0.hasValidAnswer
        }) else {
            throw StudyStoreError.invalidQuestion
        }

        var seen = Set(questions.map { normalized($0.question) })
        let newQuestions = document.questions.filter { seen.insert(normalized($0.question)).inserted }
        let updatedQuestions = questions + newQuestions
        try persist(updatedQuestions, to: "questions.json")
        questions = updatedQuestions
        return newQuestions.count
    }

    func addFlashcard(front: String, back: String) throws {
        let cleanFront = front.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanBack = back.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanFront.isEmpty, !cleanBack.isEmpty else {
            throw StudyStoreError.emptyFlashcard
        }
        let updatedFlashcards = [StudyFlashcard(front: cleanFront, back: cleanBack)] + flashcards
        try persist(updatedFlashcards, to: "flashcards.json")
        flashcards = updatedFlashcards
    }

    func deleteFlashcard(id: UUID) throws {
        let updatedFlashcards = flashcards.filter { $0.id != id }
        try persist(updatedFlashcards, to: "flashcards.json")
        flashcards = updatedFlashcards
    }

    private func normalized(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .split(whereSeparator: \.isWhitespace)
            .joined(separator: " ")
    }

    private func read<T: Decodable>(_ type: T.Type, defaultValue: T, from name: String) throws -> T {
        let url = directoryURL.appendingPathComponent(name)
        guard fileManager.fileExists(atPath: url.path) else { return defaultValue }
        return try decoder.decode(type, from: Data(contentsOf: url))
    }

    private func persist<T: Encodable>(_ value: T, to name: String) throws {
        try fileManager.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        let data = try encoder.encode(value)
        try data.write(to: directoryURL.appendingPathComponent(name), options: .atomic)
        storageError = nil
    }
}

enum StudyStoreError: LocalizedError {
    case emptyImport
    case invalidQuestion
    case emptyFlashcard

    var errorDescription: String? {
        switch self {
        case .emptyImport:
            return "The selected file contains no questions."
        case .invalidQuestion:
            return "Each question needs text, at least two options, and a valid correct-answer index."
        case .emptyFlashcard:
            return "Enter both the front and back of the flashcard."
        }
    }
}
