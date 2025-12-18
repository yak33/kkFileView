const params = new URLSearchParams(globalThis.location.search);

const isTruthy = value => value === "true" || value === "1";

const getFlag = key => isTruthy((params.get(key) ?? "").trim().toLowerCase());

const hideById = id => {
  const el = document.getElementById(id);
  if (el) {
    el.style.display = "none";
  }
};

const disablePresentationMode = getFlag("disablepresentationmode");
const disableOpenFile = getFlag("disableopenfile");
const disablePrint = getFlag("disableprint");
const disableDownload = getFlag("disabledownload");
const disableBookmark = getFlag("disablebookmark");
const disableEditing = getFlag("disableediting");

globalThis.__KKFILEVIEW_PDFJS_INK_AUTOCOMMIT__ = getFlag("inkautocommit");

if (disablePresentationMode) {
  hideById("presentationMode");
}
if (disableOpenFile) {
  hideById("secondaryOpenFile");
}
if (disablePrint) {
  hideById("printButton");
  hideById("secondaryPrint");
}
if (disableDownload) {
  hideById("downloadButton");
  hideById("secondaryDownload");
}
if (disableBookmark) {
  hideById("viewBookmark");
  hideById("viewBookmarkSeparator");
}
if (disableEditing) {
  hideById("editorModeButtons");
  hideById("editorModeSeparator");
}

const preferredFilename = (params.get("filename") ?? "")
  .replace(/[/\\]/g, "_")
  .replace(/\r|\n/g, "")
  .trim();

if (preferredFilename) {
  const applyPreferredFilename = () => {
    const app = globalThis.PDFViewerApplication;
    if (!app) {
      return false;
    }
    app._contentDispositionFilename = preferredFilename;
    return true;
  };
  queueMicrotask(() => {
    if (applyPreferredFilename()) {
      return;
    }
    setTimeout(applyPreferredFilename, 0);
  });
}

