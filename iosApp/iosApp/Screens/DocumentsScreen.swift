// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import QuickLook
import SwiftUI
import UIKit
import Shared

/// The documents Dualis has for the student.
///
/// Tapping opens the file in QuickLook; the menu on each row saves it into Files or hands it to
/// the share sheet. None of it goes through the app's own `FileViewer`: on iOS the system owns
/// all three, and the store only ever emits the bytes.
struct DocumentsScreen: View {

    /// What to do with the bytes once they arrive.
    ///
    /// `DocumentsIntent.Save` covers both keeping and sharing — the store's job ends at the
    /// download, and which sheet opens afterwards is this screen's decision. Remembering it here
    /// is the smallest place that knows.
    private enum Delivery { case saveToFiles, share }

    @Environment(AppModel.self) private var model
    @State private var previewURL: URL?
    @State private var exportURL: URL?
    @State private var shareURL: URL?
    @State private var delivery: Delivery = .saveToFiles
    @State private var downloadError: String?

    private var state: DocumentsState { model.documents.state }

    var body: some View {
        List {
            if state.requiresLogin {
                ContentUnavailableView(
                    "documents.loginRequiredTitle",
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: Text("documents.loginRequired")
                )
            } else {
                // A failed download sets `error` as well as a failed load does, so the message
                // goes above the list rather than instead of it: the documents are still there,
                // and the one that failed to download is one of them.
                if let error = state.error {
                    Section {
                        Text(error.userMessage)
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("documentsError")
                        Button("common.retry") { model.documents.dispatch(DocumentsIntentLoad()) }
                    }
                }
                if state.isLoading && state.documents.isEmpty {
                    HStack { ProgressView(); Text("common.loading").foregroundStyle(.secondary) }
                } else if state.documents.isEmpty && state.error == nil {
                    ContentUnavailableView(
                        "documents.emptyTitle",
                        systemImage: "doc.text.magnifyingglass",
                        description: Text("documents.empty")
                    )
                }
                ForEach(state.documents, id: \.self) { document in
                    row(for: document)
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("documents.title")
        // The store owns the query — the filtered list is derived from it in `DocumentsState`, so
        // there is no second copy of "what is being searched for" here.
        .searchable(text: searchBinding, prompt: Text("documents.searchPrompt"))
        .refreshable { model.documents.dispatch(DocumentsIntentRefresh()) }
        .task {
            model.documents.dispatch(DocumentsIntentEnsureLoaded())
            model.documents.onEffect { effect in
                switch effect {
                case let open as DocumentsEffectOpenFile:
                    previewURL = writeToTemporaryFile(name: open.fileName, bytes: open.bytes)
                case let save as DocumentsEffectSaveFile:
                    let url = writeToTemporaryFile(name: save.fileName, bytes: save.bytes)
                    switch delivery {
                    case .saveToFiles: exportURL = url
                    case .share: shareURL = url
                    }
                case let failed as DocumentsEffectDownloadFailed:
                    downloadError = failed.error.userMessage
                default:
                    break
                }
            }
        }
        .sheet(isPresented: binding($previewURL)) {
            if let previewURL {
                QuickLookSheet(url: previewURL).ignoresSafeArea()
            }
        }
        .sheet(isPresented: binding($exportURL)) {
            if let exportURL {
                DocumentExportSheet(url: exportURL)
            }
        }
        .sheet(isPresented: binding($shareURL)) {
            if let shareURL {
                ActivitySheet(url: shareURL)
            }
        }
        .alert(
            Text("common.error"),
            isPresented: Binding(get: { downloadError != nil }, set: { if !$0 { downloadError = nil } })
        ) {
            Button("common.ok", role: .cancel) { downloadError = nil }
        } message: {
            Text(downloadError ?? "")
        }
        .accessibilityIdentifier("documentsScreen")
    }

    private func row(for document: DualisDocument) -> some View {
        HStack {
            Button {
                model.documents.dispatch(DocumentsIntentOpen(document: document))
            } label: {
                DocumentRow(document: document, isDownloading: state.isDownloading(document: document))
            }
            .buttonStyle(.plain)

            Menu {
                Button {
                    model.documents.dispatch(DocumentsIntentOpen(document: document))
                } label: {
                    Label("documents.open", systemImage: "eye")
                }
                Button {
                    save(document, as: .saveToFiles)
                } label: {
                    Label("documents.saveToFiles", systemImage: "folder.badge.plus")
                }
                Button {
                    save(document, as: .share)
                } label: {
                    Label("documents.share", systemImage: "square.and.arrow.up")
                }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .foregroundStyle(Color.brand)
                    .padding(.leading, 8)
            }
            .accessibilityLabel(Text("documents.actions"))
        }
        .swipeActions(edge: .trailing) {
            Button {
                save(document, as: .saveToFiles)
            } label: {
                Label("documents.saveToFiles", systemImage: "folder.badge.plus")
            }
            .tint(Color.brand)
            Button {
                save(document, as: .share)
            } label: {
                Label("documents.share", systemImage: "square.and.arrow.up")
            }
            .tint(.gray)
        }
    }

    private func save(_ document: DualisDocument, as delivery: Delivery) {
        self.delivery = delivery
        model.documents.dispatch(DocumentsIntentSave(document: document))
    }

    private func binding(_ url: Binding<URL?>) -> Binding<Bool> {
        Binding(get: { url.wrappedValue != nil }, set: { if !$0 { url.wrappedValue = nil } })
    }

    private var searchBinding: Binding<String> {
        Binding(
            get: { state.searchQuery },
            set: { model.documents.dispatch(DocumentsIntentSearchChanged(query: $0)) }
        )
    }

    /// QuickLook, the share sheet and the Files picker all work on files, not on bytes.
    ///
    /// The temporary directory is cleaned by the system, and the name is kept so the preview and
    /// the save dialog show what Dualis called the document instead of a UUID.
    private func writeToTemporaryFile(name: String, bytes: KotlinByteArray) -> URL? {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let url = directory.appendingPathComponent(name.isEmpty ? "document.pdf" : name)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try bytes.toNSData().write(to: url)
            return url
        } catch {
            downloadError = String(localized: "error.storage")
            return nil
        }
    }
}

private struct DocumentRow: View {

    let document: DualisDocument
    let isDownloading: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.text.fill")
                .foregroundStyle(Color.brand)
                .frame(width: 24)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(document.title)
                Text("\(document.date) \(document.time)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if isDownloading {
                ProgressView()
            }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

/// `QLPreviewController`. SwiftUI's own `.quickLookPreview` modifier exists on macOS only.
private struct QuickLookSheet: UIViewControllerRepresentable {

    let url: URL

    func makeCoordinator() -> Coordinator { Coordinator(url: url) }

    func makeUIViewController(context: Context) -> QLPreviewController {
        let controller = QLPreviewController()
        controller.dataSource = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: QLPreviewController, context: Context) {
        context.coordinator.url = url
        controller.reloadData()
    }

    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        var url: URL
        init(url: URL) { self.url = url }
        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
            url as NSURL
        }
    }
}

/// "Save to Files": the system's own export picker, so the document lands wherever the user keeps
/// their documents — iCloud Drive, On My iPhone, or any provider they have installed.
private struct DocumentExportSheet: UIViewControllerRepresentable {

    let url: URL

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        // asCopy, because the source is a temporary file the system may delete underneath a move.
        UIDocumentPickerViewController(forExporting: [url], asCopy: true)
    }

    func updateUIViewController(_ controller: UIDocumentPickerViewController, context: Context) {}
}

/// `UIActivityViewController`, which SwiftUI has no equivalent for when the URL only exists after
/// the download has finished — `ShareLink` needs its item at the time the view is built.
private struct ActivitySheet: UIViewControllerRepresentable {

    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
