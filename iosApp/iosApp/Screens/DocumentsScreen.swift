// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import QuickLook
import SwiftUI
import Shared
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

/// The documents Dualis has for the student.
///
/// Tapping opens the file in QuickLook; the menu on each row saves it into Files or hands it to
/// the share sheet. None of it goes through the app's own `FileViewer`: on iOS the system owns
/// all three, and the store only ever emits the bytes.
struct DocumentsScreen: View {

    @Environment(\.accentChoice) private var accentChoice

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
    /// The row whose download is in flight, so a finished save knows which row to show the share
    /// picker from. The Mac picker anchors to that row's Share button.
    @State private var pendingDocument: DualisDocument?
    @State private var sharingDocument: DualisDocument?

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
        .platformGroupedList()
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
                    case .saveToFiles:
                        #if os(iOS)
                        exportURL = url
                        #else
                        if let url { saveWithPanel(url) }
                        #endif
                    case .share:
                        shareURL = url
                        sharingDocument = pendingDocument
                    }
                case let failed as DocumentsEffectDownloadFailed:
                    downloadError = failed.error.userMessage
                default:
                    break
                }
            }
        }
        // QuickLook is the same SwiftUI modifier on both platforms: a panel on the Mac, the
        // full-screen preview on iOS.
        .quickLookPreview($previewURL)
        #if os(iOS)
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
        #endif
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

            #if os(macOS)
            if state.isDownloading(document: document) {
                ProgressView()
                    .controlSize(.small)
            } else {
                macActions(for: document)
            }
            #else
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
                    .foregroundStyle(accentChoice.accent)
                    .padding(.leading, 8)
            }
            .accessibilityLabel(Text("documents.actions"))
            #endif
        }
        #if os(macOS)
        // The separator follows these guides; without them it starts at the first Text it finds,
        // which is inside the trailing buttons, and the line stops short of the row.
        .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
        .alignmentGuide(.listRowSeparatorTrailing) { dimensions in dimensions.width }
        .contextMenu {
            Button {
                model.documents.dispatch(DocumentsIntentOpen(document: document))
            } label: {
                Label("documents.open", systemImage: "eye")
            }
            Button {
                save(document, as: .saveToFiles)
            } label: {
                Label("documents.save", systemImage: "square.and.arrow.down")
            }
            Button {
                save(document, as: .share)
            } label: {
                Label("documents.share", systemImage: "square.and.arrow.up")
            }
        }
        #endif
        .swipeActions(edge: .trailing) {
            Button {
                save(document, as: .saveToFiles)
            } label: {
                Label("documents.saveToFiles", systemImage: "folder.badge.plus")
            }
            .tint(accentChoice.accent)
            Button {
                save(document, as: .share)
            } label: {
                Label("documents.share", systemImage: "square.and.arrow.up")
            }
            .tint(.gray)
        }
    }

    #if os(macOS)
    /// The three row actions as visible buttons at the trailing edge. The same actions are in the
    /// row's context menu, so the buttons are the primary path and the menu the secondary one.
    ///
    /// The share picker hangs from the Share button itself: the background of that button is the
    /// anchor view, and it exists only while this row is the one being shared.
    private func macActions(for document: DualisDocument) -> some View {
        HStack(spacing: 8) {
            Button {
                model.documents.dispatch(DocumentsIntentOpen(document: document))
            } label: {
                Label("documents.open", systemImage: "eye")
            }
            .help(Text("documents.open"))

            Button {
                save(document, as: .saveToFiles)
            } label: {
                Label("documents.save", systemImage: "square.and.arrow.down")
            }
            .help(Text("documents.save"))

            Button {
                save(document, as: .share)
            } label: {
                Label("documents.share", systemImage: "square.and.arrow.up")
            }
            .help(Text("documents.share"))
            .background {
                if sharingDocument == document, let shareURL {
                    SharingPicker(url: shareURL).id(shareURL)
                }
            }
        }
        .buttonStyle(.bordered)
        .controlSize(.regular)
    }
    #endif

    private func save(_ document: DualisDocument, as delivery: Delivery) {
        self.delivery = delivery
        pendingDocument = document
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

    #if os(macOS)
    /// "Save" on a Mac: the standard save panel, then a copy to wherever the user picks.
    private func saveWithPanel(_ source: URL) {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = source.lastPathComponent
        panel.canCreateDirectories = true
        panel.begin { response in
            guard response == .OK, let destination = panel.url else { return }
            do {
                if FileManager.default.fileExists(atPath: destination.path) {
                    try FileManager.default.removeItem(at: destination)
                }
                try FileManager.default.copyItem(at: source, to: destination)
            } catch {
                downloadError = String(localized: "error.storage")
            }
        }
    }
    #endif

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

    @Environment(\.accentChoice) private var accentChoice
    let document: DualisDocument
    let isDownloading: Bool

    #if os(macOS)
    // A Mac list row is a pointer target and reads cramped at the iOS sizes.
    private static let iconWidth: CGFloat = 30
    private static let minRowHeight: CGFloat = 56
    private static let verticalPadding: CGFloat = 6
    private static let lineSpacing: CGFloat = 3
    private static let subtitleFont: Font = .callout
    #else
    private static let iconWidth: CGFloat = 24
    private static let minRowHeight: CGFloat = 0
    private static let verticalPadding: CGFloat = 0
    private static let lineSpacing: CGFloat = 2
    private static let subtitleFont: Font = .caption
    #endif

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.text.fill")
                .foregroundStyle(accentChoice.accent)
                .frame(width: Self.iconWidth)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Self.lineSpacing) {
                Text(document.title)
                Text("\(document.date) \(document.time)")
                    .font(Self.subtitleFont)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            #if os(iOS)
            if isDownloading {
                ProgressView()
            }
            #endif
        }
        .frame(minHeight: Self.minRowHeight)
        .padding(.vertical, Self.verticalPadding)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

#if os(iOS)
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
#else
/// The Mac share menu for `url`, shown from this window.
///
/// `NSSharingServicePicker` is a popover that needs a view to hang from, not a sheet, so an
/// invisible view carries it. It is created for one file at a time (`.id(url)` in the caller), and
/// the picker is shown as the view appears, once the window has it.
private struct SharingPicker: NSViewRepresentable {

    let url: URL

    func makeNSView(context: Context) -> NSView {
        let anchor = NSView()
        DispatchQueue.main.async {
            NSSharingServicePicker(items: [url])
                .show(relativeTo: anchor.bounds, of: anchor, preferredEdge: .minY)
        }
        return anchor
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}
#endif
