import SwiftUI
import UniformTypeIdentifiers

enum RovexTheme: String, CaseIterable, Identifiable {
    case amoled = "AMOLED"
    case light = "Light"
    case pastel = "Pastel"
    case mint = "Mint"
    case sunset = "Sunset"
    case lavender = "Lavender"

    var id: String { rawValue }

    var palette: ThemePalette { ThemePalette(theme: self) }

    var preferredScheme: ColorScheme? { self == .amoled ? .dark : .light }
}

struct ThemePalette {
    let theme: RovexTheme

    var background: Color {
        switch theme {
        case .amoled: Color(red: 0.015, green: 0.02, blue: 0.035)
        case .light: Color(red: 0.96, green: 0.97, blue: 0.99)
        case .pastel: Color(red: 0.99, green: 0.95, blue: 0.97)
        case .mint: Color(red: 0.91, green: 0.97, blue: 0.94)
        case .sunset: Color(red: 0.99, green: 0.93, blue: 0.88)
        case .lavender: Color(red: 0.94, green: 0.92, blue: 0.99)
        }
    }

    var surface: Color {
        switch theme {
        case .amoled: Color(red: 0.075, green: 0.085, blue: 0.12)
        case .light: .white
        case .pastel: Color(red: 1.0, green: 0.985, blue: 0.99)
        case .mint: Color(red: 0.98, green: 1.0, blue: 0.99)
        case .sunset: Color(red: 1.0, green: 0.98, blue: 0.95)
        case .lavender: Color(red: 0.99, green: 0.98, blue: 1.0)
        }
    }

    var accent: Color {
        switch theme {
        case .amoled: Color(red: 0.52, green: 0.76, blue: 1.0)
        case .light: Color(red: 0.16, green: 0.36, blue: 0.78)
        case .pastel: Color(red: 0.68, green: 0.34, blue: 0.57)
        case .mint: Color(red: 0.08, green: 0.48, blue: 0.36)
        case .sunset: Color(red: 0.72, green: 0.28, blue: 0.17)
        case .lavender: Color(red: 0.42, green: 0.32, blue: 0.72)
        }
    }

    var primaryText: Color { theme == .amoled ? Color(red: 0.95, green: 0.96, blue: 1.0) : Color(red: 0.12, green: 0.14, blue: 0.19) }
    var secondaryText: Color { theme == .amoled ? Color(red: 0.70, green: 0.73, blue: 0.80) : Color(red: 0.36, green: 0.39, blue: 0.46) }
    var border: Color { theme == .amoled ? Color.white.opacity(0.12) : Color.black.opacity(0.07) }
}

struct ContentView: View {
    @StateObject private var store = LocalStudyStore.shared
    @AppStorage("rovex.theme") private var selectedTheme = RovexTheme.amoled.rawValue
    @State private var selectedTab = 0

    private var theme: RovexTheme { RovexTheme(rawValue: selectedTheme) ?? .amoled }
    private var palette: ThemePalette { theme.palette }

    var body: some View {
        TabView(selection: $selectedTab) {
            DashboardView(store: store, theme: theme, selectedTab: $selectedTab)
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(0)
            QBankView(store: store, theme: theme)
                .tabItem { Label("QBank", systemImage: "text.book.closed.fill") }
                .tag(1)
            FlashcardsView(store: store, theme: theme)
                .tabItem { Label("Flashcards", systemImage: "rectangle.on.rectangle") }
                .tag(2)
            BenView(theme: theme)
                .tabItem { Label("Ben", systemImage: "brain.head.profile") }
                .tag(3)
            SettingsView(store: store, theme: theme)
                .tabItem { Label("Settings", systemImage: "slider.horizontal.3") }
                .tag(4)
        }
        .tint(palette.accent)
        .preferredColorScheme(theme.preferredScheme)
    }
}

struct DashboardView: View {
    @ObservedObject var store: LocalStudyStore
    let theme: RovexTheme
    @Binding var selectedTab: Int
    private var p: ThemePalette { theme.palette }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("ROVEX").font(.system(size: 13, weight: .bold, design: .rounded)).tracking(3).foregroundStyle(p.accent)
                        Text("Your study space").font(.system(size: 30, weight: .bold, design: .rounded)).foregroundStyle(p.primaryText)
                        Text("A private, offline-first foundation for focused clinical learning.")
                            .font(.subheadline).foregroundStyle(p.secondaryText)
                    }
                    HStack(spacing: 12) {
                        MetricCard(title: "Questions", value: "\(store.questions.count)", symbol: "text.book.closed", theme: theme)
                        MetricCard(title: "Flashcards", value: "\(store.flashcards.count)", symbol: "rectangle.on.rectangle", theme: theme)
                    }
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Continue studying").font(.title3.bold()).foregroundStyle(p.primaryText)
                        ActionRow(title: "Question Bank", detail: "Import and solve your own question sets", symbol: "book.closed.fill", theme: theme) { selectedTab = 1 }
                        ActionRow(title: "Flashcards", detail: "Create cards and review them offline", symbol: "rectangle.on.rectangle.fill", theme: theme) { selectedTab = 2 }
                        ActionRow(title: "Ben", detail: "Local assistant foundation and capability status", symbol: "brain.head.profile", theme: theme) { selectedTab = 3 }
                    }
                    if let error = store.storageError {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote).foregroundStyle(.orange)
                            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                            .background(p.surface, in: RoundedRectangle(cornerRadius: 14))
                    }
                    Text("Your study files stay on this device in this initial iOS build. Android data is not automatically synced.")
                        .font(.footnote).foregroundStyle(p.secondaryText)
                        .padding(.top, 4)
                }
                .padding(20)
            }
            .background(p.background.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
        }
    }
}

struct MetricCard: View {
    let title: String
    let value: String
    let symbol: String
    let theme: RovexTheme
    private var p: ThemePalette { theme.palette }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: symbol).font(.title3).foregroundStyle(p.accent)
            Text(value).font(.system(size: 28, weight: .bold, design: .rounded)).foregroundStyle(p.primaryText)
            Text(title).font(.subheadline.weight(.medium)).foregroundStyle(p.secondaryText)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(p.surface, in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(p.border, lineWidth: 1))
    }
}

struct ActionRow: View {
    let title: String
    let detail: String
    let symbol: String
    let theme: RovexTheme
    let action: () -> Void
    private var p: ThemePalette { theme.palette }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: symbol).font(.title3).foregroundStyle(p.accent)
                    .frame(width: 44, height: 44).background(p.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(p.primaryText)
                    Text(detail).font(.caption).foregroundStyle(p.secondaryText).multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(p.secondaryText)
            }
            .padding(13).background(p.surface, in: RoundedRectangle(cornerRadius: 17))
            .overlay(RoundedRectangle(cornerRadius: 17).stroke(p.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct QBankView: View {
    @ObservedObject var store: LocalStudyStore
    let theme: RovexTheme
    @State private var showingImporter = false
    @State private var importMessage: String?
    @State private var importError = false
    private var p: ThemePalette { theme.palette }

    var body: some View {
        NavigationStack {
            Group {
                if store.questions.isEmpty {
                    ContentUnavailableView {
                        Label("No questions yet", systemImage: "text.book.closed")
                    } description: {
                        Text("Import a JSON question set stored on your device to begin. No account or network is required.")
                    } actions: {
                        Button("Import JSON") { showingImporter = true }.buttonStyle(.borderedProminent)
                    }
                } else {
                    List {
                        Section {
                            ForEach(store.questions.indices, id: \.self) { index in
                                let question = store.questions[index]
                                NavigationLink {
                                    QuestionDetailView(question: question, number: index + 1, theme: theme)
                                } label: {
                                    VStack(alignment: .leading, spacing: 5) {
                                        Text("Question \(index + 1)").font(.caption.weight(.semibold)).foregroundStyle(p.accent)
                                        Text(question.question).font(.body).foregroundStyle(p.primaryText).lineLimit(3)
                                    }.padding(.vertical, 5)
                                }
                                .listRowBackground(p.surface)
                            }
                        } header: {
                            Text("\(store.questions.count) imported questions")
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .background(p.background.ignoresSafeArea())
            .navigationTitle("Question Bank")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showingImporter = true } label: { Image(systemName: "square.and.arrow.down") }
                        .accessibilityLabel("Import question JSON")
                }
            }
            .fileImporter(isPresented: $showingImporter, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
                handleImport(result)
            }
            .alert(importError ? "Import failed" : "Import complete", isPresented: Binding(
                get: { importMessage != nil },
                set: { if !$0 { importMessage = nil } }
            )) {
                Button("OK", role: .cancel) { importMessage = nil }
            } message: {
                Text(importMessage ?? "")
            }
        }
    }

    private func handleImport(_ result: Result<[URL], Error>) {
        do {
            let urls = try result.get()
            guard let url = urls.first else { return }
            let hasAccess = url.startAccessingSecurityScopedResource()
            defer { if hasAccess { url.stopAccessingSecurityScopedResource() } }
            let data = try Data(contentsOf: url)
            let count = try store.importQuestions(from: data)
            importError = false
            importMessage = "Added \(count) new question(s). Duplicate question stems were skipped."
        } catch {
            importError = true
            importMessage = error.localizedDescription
        }
    }
}

struct QuestionDetailView: View {
    let question: QBankQuestion
    let number: Int
    let theme: RovexTheme
    @State private var selectedOption: Int?
    @State private var revealed = false
    private var p: ThemePalette { theme.palette }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("QUESTION \(number)").font(.caption.bold()).tracking(1.5).foregroundStyle(p.accent)
                Text(question.question).font(.title3.weight(.semibold)).foregroundStyle(p.primaryText)
                ForEach(question.options.indices, id: \.self) { index in
                    Button {
                        if !revealed { selectedOption = index }
                    } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Text(String(UnicodeScalar(65 + index)!)).font(.headline)
                                .frame(width: 28, height: 28).background(p.accent.opacity(0.12), in: Circle())
                            Text(question.options[index]).frame(maxWidth: .infinity, alignment: .leading)
                            if revealed && index == question.correctAnswerIndex {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                            } else if revealed && selectedOption == index {
                                Image(systemName: "xmark.circle.fill").foregroundStyle(.red)
                            }
                        }
                        .font(.body).foregroundStyle(p.primaryText).padding(14)
                        .background(optionBackground(index), in: RoundedRectangle(cornerRadius: 15))
                        .overlay(RoundedRectangle(cornerRadius: 15).stroke(optionBorder(index), lineWidth: 1))
                    }.buttonStyle(.plain)
                }
                if revealed {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(selectedOption == question.correctAnswerIndex ? "Correct" : "Answer")
                            .font(.headline).foregroundStyle(selectedOption == question.correctAnswerIndex ? .green : p.accent)
                        Text(question.options[question.correctAnswerIndex]).font(.body.weight(.medium)).foregroundStyle(p.primaryText)
                        if !question.explanation.isEmpty {
                            Text(question.explanation).font(.body).foregroundStyle(p.secondaryText)
                        }
                    }.frame(maxWidth: .infinity, alignment: .leading).padding(16)
                        .background(p.surface, in: RoundedRectangle(cornerRadius: 16))
                }
                Button(revealed ? "Try again" : "Reveal answer") {
                    if revealed { selectedOption = nil; revealed = false } else { revealed = true }
                }
                .buttonStyle(.borderedProminent).tint(p.accent)
                .disabled(!revealed && selectedOption == nil)
            }.padding(20)
        }
        .background(p.background.ignoresSafeArea())
        .navigationTitle("Solve")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func optionBackground(_ index: Int) -> Color {
        guard revealed else { return p.surface }
        if index == question.correctAnswerIndex { return Color.green.opacity(0.12) }
        if index == selectedOption { return Color.red.opacity(0.10) }
        return p.surface
    }

    private func optionBorder(_ index: Int) -> Color {
        guard revealed else { return index == selectedOption ? p.accent : p.border }
        if index == question.correctAnswerIndex { return .green.opacity(0.55) }
        if index == selectedOption { return .red.opacity(0.55) }
        return p.border
    }
}

struct FlashcardsView: View {
    @ObservedObject var store: LocalStudyStore
    let theme: RovexTheme
    @State private var showingAdd = false
    @State private var activeIndex = 0
    @State private var showingBack = false
    @State private var actionError: String?
    private var p: ThemePalette { theme.palette }

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                if store.flashcards.isEmpty {
                    ContentUnavailableView {
                        Label("Your deck is empty", systemImage: "rectangle.on.rectangle")
                    } description: {
                        Text("Create your first card. Cards are saved locally on this iPhone or iPad.")
                    } actions: {
                        Button("Create flashcard") { showingAdd = true }.buttonStyle(.borderedProminent)
                    }
                } else {
                    Text("CARD \(min(activeIndex + 1, store.flashcards.count)) OF \(store.flashcards.count)")
                        .font(.caption.bold()).tracking(1.3).foregroundStyle(p.secondaryText)
                    Button { showingBack.toggle() } label: {
                        VStack(spacing: 18) {
                            Image(systemName: showingBack ? "arrow.uturn.backward" : "hand.tap")
                                .font(.title2).foregroundStyle(p.accent)
                            Text(showingBack ? "ANSWER" : "PROMPT").font(.caption.bold()).tracking(2).foregroundStyle(p.accent)
                            Text(currentCardText).font(.title2.weight(.semibold)).foregroundStyle(p.primaryText).multilineTextAlignment(.center)
                            Text("Tap to flip").font(.caption).foregroundStyle(p.secondaryText)
                        }
                        .frame(maxWidth: .infinity, minHeight: 260)
                        .padding(24).background(p.surface, in: RoundedRectangle(cornerRadius: 25))
                        .overlay(RoundedRectangle(cornerRadius: 25).stroke(p.border, lineWidth: 1))
                    }.buttonStyle(.plain).padding(.horizontal, 20)
                    HStack(spacing: 14) {
                        Button("Previous") { move(-1) }.buttonStyle(.bordered).disabled(activeIndex == 0)
                        Button("Next") { move(1) }.buttonStyle(.borderedProminent).tint(p.accent).disabled(activeIndex >= store.flashcards.count - 1)
                    }
                    List {
                        ForEach(store.flashcards) { card in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(card.front).font(.headline).foregroundStyle(p.primaryText)
                                Text(card.back).font(.subheadline).foregroundStyle(p.secondaryText).lineLimit(2)
                            }.listRowBackground(p.surface)
                        }
                        .onDelete(perform: deleteCards)
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .background(p.background.ignoresSafeArea())
            .navigationTitle("Flashcards")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showingAdd = true } label: { Image(systemName: "plus") }.accessibilityLabel("Add flashcard")
                }
            }
            .sheet(isPresented: $showingAdd) {
                AddFlashcardView(theme: theme) { front, back in
                    try store.addFlashcard(front: front, back: back)
                    activeIndex = 0
                    showingBack = false
                }
                .presentationDetents([.medium, .large])
            }
            .alert("Unable to save card", isPresented: Binding(
                get: { actionError != nil },
                set: { if !$0 { actionError = nil } }
            )) {
                Button("OK", role: .cancel) { actionError = nil }
            } message: { Text(actionError ?? "") }
        }
    }

    private var currentCardText: String {
        guard store.flashcards.indices.contains(activeIndex) else { return "" }
        let card = store.flashcards[activeIndex]
        return showingBack ? card.back : card.front
    }

    private func move(_ delta: Int) {
        activeIndex = min(max(0, activeIndex + delta), max(0, store.flashcards.count - 1))
        showingBack = false
    }

    private func deleteCards(at offsets: IndexSet) {
        let ids = offsets.map { store.flashcards[$0].id }
        do {
            for id in ids { try store.deleteFlashcard(id: id) }
            activeIndex = min(activeIndex, max(0, store.flashcards.count - 1))
            showingBack = false
        } catch {
            actionError = error.localizedDescription
        }
    }
}

struct AddFlashcardView: View {
    let theme: RovexTheme
    let onSave: (String, String) throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var front = ""
    @State private var back = ""
    @State private var errorMessage: String?
    private var p: ThemePalette { theme.palette }

    var body: some View {
        NavigationStack {
            Form {
                Section("Prompt") {
                    TextField("Term or question", text: $front, axis: .vertical).lineLimit(2...5)
                }
                Section("Answer") {
                    TextField("Definition or answer", text: $back, axis: .vertical).lineLimit(3...7)
                }
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red).font(.footnote)
                }
            }
            .scrollContentBackground(.hidden)
            .background(p.background)
            .navigationTitle("New flashcard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        do { try onSave(front, back); dismiss() }
                        catch { errorMessage = error.localizedDescription }
                    }.disabled(front.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || back.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

struct BenView: View {
    let theme: RovexTheme
    private var p: ThemePalette { theme.palette }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Image(systemName: "brain.head.profile").font(.system(size: 46)).foregroundStyle(p.accent)
                        .frame(maxWidth: .infinity, alignment: .center).padding(.top, 20)
                    Text("Ben").font(.largeTitle.bold()).foregroundStyle(p.primaryText)
                    Text("Offline cognitive assistant").font(.headline).foregroundStyle(p.accent)
                    Text("This iOS foundation does not yet contain the Android Ben engine or a neural model. The assistant is intentionally marked as unavailable rather than presenting simulated AI answers as real.")
                        .font(.body).foregroundStyle(p.secondaryText)
                    Label("Local QBank and flashcards work without a network connection.", systemImage: "checkmark.shield.fill")
                        .font(.subheadline).foregroundStyle(p.primaryText)
                    Label("Ben model, retrieval index, and Android data migration are not connected yet.", systemImage: "clock.badge.exclamationmark")
                        .font(.subheadline).foregroundStyle(p.primaryText)
                }.padding(22)
            }
            .background(p.background.ignoresSafeArea())
            .navigationTitle("Ben")
        }
    }
}

struct StudyArchiveDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.data = data
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

struct SettingsView: View {
    @ObservedObject var store: LocalStudyStore
    let theme: RovexTheme
    @AppStorage("rovex.theme") private var selectedTheme = RovexTheme.amoled.rawValue
    @State private var showingImporter = false
    @State private var showingExporter = false
    @State private var exportDocument = StudyArchiveDocument(data: Data())
    @State private var transferMessage: String?
    @State private var transferIsError = false
    private var p: ThemePalette { theme.palette }

    var body: some View {
        NavigationStack {
            Form {
                Section("Appearance") {
                    Picker("Theme", selection: $selectedTheme) {
                        ForEach(RovexTheme.allCases) { theme in
                            Text(theme.rawValue).tag(theme.rawValue)
                        }
                    }
                    .pickerStyle(.navigationLink)
                    Text("Theme changes are stored on this device and apply across the app.")
                        .font(.footnote).foregroundStyle(p.secondaryText)
                }
                Section("Study data transfer") {
                    Button {
                        do {
                            exportDocument = StudyArchiveDocument(data: try store.exportData())
                            showingExporter = true
                        } catch {
                            transferIsError = true
                            transferMessage = error.localizedDescription
                        }
                    } label: {
                        Label("Export study data", systemImage: "square.and.arrow.up")
                    }
                    Button {
                        showingImporter = true
                    } label: {
                        Label("Import study data", systemImage: "square.and.arrow.down")
                    }
                    Text("Exports questions and flashcards into one versioned JSON archive. Import merges new records and skips duplicates.")
                        .font(.footnote).foregroundStyle(p.secondaryText)
                }
                Section("Data and privacy") {
                    Label("Question banks and flashcards are stored locally.", systemImage: "internaldrive")
                    Label("No account or network is required for these features.", systemImage: "wifi.slash")
                    Text("This initial iOS build does not sync data with Android. Import/export and migration tools are planned separately.")
                        .font(.footnote).foregroundStyle(p.secondaryText)
                }
                Section("Build status") {
                    LabeledContent("Platform", value: "Native SwiftUI")
                    LabeledContent("AI assistant", value: "Not connected")
                    LabeledContent("Question import", value: "JSON")
                }
            }
            .scrollContentBackground(.hidden)
            .background(p.background)
            .tint(p.accent)
            .navigationTitle("Settings")
            .fileImporter(isPresented: $showingImporter, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
                do {
                    let urls = try result.get()
                    guard let url = urls.first else { return }
                    let hasAccess = url.startAccessingSecurityScopedResource()
                    defer { if hasAccess { url.stopAccessingSecurityScopedResource() } }
                    let counts = try store.importData(from: Data(contentsOf: url))
                    transferIsError = false
                    transferMessage = "Added \(counts.questionsAdded) question(s) and \(counts.flashcardsAdded) flashcard(s)."
                } catch {
                    transferIsError = true
                    transferMessage = error.localizedDescription
                }
            }
            .fileExporter(isPresented: $showingExporter, document: exportDocument, contentType: .json, defaultFilename: "rovex-study-data") { result in
                switch result {
                case .success:
                    transferIsError = false
                    transferMessage = "Study archive exported successfully."
                case .failure(let error):
                    transferIsError = true
                    transferMessage = error.localizedDescription
                }
            }
            .alert(transferIsError ? "Transfer failed" : "Study data", isPresented: Binding(
                get: { transferMessage != nil },
                set: { if !$0 { transferMessage = nil } }
            )) {
                Button("OK", role: .cancel) { transferMessage = nil }
            } message: {
                Text(transferMessage ?? "")
            }
        }
    }
}
