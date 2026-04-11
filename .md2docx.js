// Markdown → DOCX converter for Korean learning docs
// Usage: node .md2docx.js <input.md> <output.docx>
// Supports: headings, paragraphs, bullet/numbered lists, tables, code blocks,
// inline bold/italic/code, blockquotes, horizontal rules.

const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  HeadingLevel, AlignmentType, LevelFormat, BorderStyle, WidthType, ShadingType,
  PageOrientation,
} = require("docx");

const [, , inputPath, outputPath] = process.argv;
if (!inputPath || !outputPath) {
  console.error("Usage: node .md2docx.js <input.md> <output.docx>");
  process.exit(1);
}

const FONT = "Malgun Gothic"; // Korean-capable default on Windows
const MONO = "Consolas";

// ---------- Inline parser ----------
// Handles **bold**, *italic*, `code`, and escaped backticks.
function parseInline(text) {
  const runs = [];
  let i = 0;
  const push = (t, opts = {}) => {
    if (t === "") return;
    runs.push(new TextRun({ text: t, font: opts.code ? MONO : FONT, ...opts }));
  };

  let buf = "";
  const flush = () => { if (buf) { push(buf); buf = ""; } };

  while (i < text.length) {
    const ch = text[i];
    // inline code
    if (ch === "`") {
      flush();
      const end = text.indexOf("`", i + 1);
      if (end === -1) { buf += ch; i++; continue; }
      const code = text.slice(i + 1, end);
      push(code, { code: true, shading: { type: ShadingType.CLEAR, fill: "F2F2F2" } });
      i = end + 1; continue;
    }
    // bold **..**
    if (ch === "*" && text[i + 1] === "*") {
      flush();
      const end = text.indexOf("**", i + 2);
      if (end === -1) { buf += ch; i++; continue; }
      push(text.slice(i + 2, end), { bold: true });
      i = end + 2; continue;
    }
    // italic *..*
    if (ch === "*") {
      flush();
      const end = text.indexOf("*", i + 1);
      if (end === -1) { buf += ch; i++; continue; }
      push(text.slice(i + 1, end), { italics: true });
      i = end + 1; continue;
    }
    // links [text](url) → just show text
    if (ch === "[") {
      const close = text.indexOf("](", i);
      if (close !== -1) {
        const endParen = text.indexOf(")", close);
        if (endParen !== -1) {
          flush();
          push(text.slice(i + 1, close), { color: "0563C1", underline: {} });
          i = endParen + 1; continue;
        }
      }
    }
    buf += ch; i++;
  }
  flush();
  if (runs.length === 0) push("");
  return runs;
}

// ---------- Block parser ----------
function parseMarkdown(md) {
  const lines = md.replace(/\r\n/g, "\n").split("\n");
  const blocks = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // Fenced code block
    if (/^```/.test(line)) {
      const lang = line.slice(3).trim();
      const codeLines = [];
      i++;
      while (i < lines.length && !/^```/.test(lines[i])) {
        codeLines.push(lines[i]); i++;
      }
      i++; // skip closing fence
      blocks.push({ type: "code", lang, lines: codeLines });
      continue;
    }

    // Heading
    const h = /^(#{1,6})\s+(.*)$/.exec(line);
    if (h) {
      blocks.push({ type: "heading", level: h[1].length, text: h[2].trim() });
      i++; continue;
    }

    // Horizontal rule
    if (/^---+\s*$/.test(line) || /^\*\*\*+\s*$/.test(line)) {
      blocks.push({ type: "hr" });
      i++; continue;
    }

    // Table (must have | and next line must be divider)
    if (/\|/.test(line) && i + 1 < lines.length && /^\s*\|?[\s\-:|]+\|?\s*$/.test(lines[i + 1]) && /-/.test(lines[i + 1])) {
      const header = splitRow(line);
      i += 2; // skip divider
      const rows = [];
      while (i < lines.length && /\|/.test(lines[i]) && lines[i].trim() !== "") {
        rows.push(splitRow(lines[i]));
        i++;
      }
      blocks.push({ type: "table", header, rows });
      continue;
    }

    // Blockquote
    if (/^>\s?/.test(line)) {
      const quoteLines = [];
      while (i < lines.length && /^>\s?/.test(lines[i])) {
        quoteLines.push(lines[i].replace(/^>\s?/, ""));
        i++;
      }
      blocks.push({ type: "quote", text: quoteLines.join(" ") });
      continue;
    }

    // Bullet list
    if (/^(\s*)[-*+]\s+/.test(line)) {
      const items = [];
      while (i < lines.length && /^(\s*)[-*+]\s+/.test(lines[i])) {
        const m = /^(\s*)[-*+]\s+(.*)$/.exec(lines[i]);
        const indent = Math.floor(m[1].length / 2);
        items.push({ level: indent, text: m[2] });
        i++;
      }
      blocks.push({ type: "ul", items });
      continue;
    }

    // Numbered list
    if (/^(\s*)\d+\.\s+/.test(line)) {
      const items = [];
      while (i < lines.length && /^(\s*)\d+\.\s+/.test(lines[i])) {
        const m = /^(\s*)\d+\.\s+(.*)$/.exec(lines[i]);
        const indent = Math.floor(m[1].length / 2);
        items.push({ level: indent, text: m[2] });
        i++;
      }
      blocks.push({ type: "ol", items });
      continue;
    }

    // Blank line
    if (line.trim() === "") { i++; continue; }

    // Paragraph (collect until blank/special)
    const paraLines = [line];
    i++;
    while (i < lines.length && lines[i].trim() !== ""
      && !/^#{1,6}\s/.test(lines[i])
      && !/^```/.test(lines[i])
      && !/^---+\s*$/.test(lines[i])
      && !/^(\s*)[-*+]\s+/.test(lines[i])
      && !/^(\s*)\d+\.\s+/.test(lines[i])
      && !/^>\s?/.test(lines[i])
      && !(/\|/.test(lines[i]) && i + 1 < lines.length && /^\s*\|?[\s\-:|]+\|?\s*$/.test(lines[i + 1]) && /-/.test(lines[i + 1]))
    ) {
      paraLines.push(lines[i]);
      i++;
    }
    blocks.push({ type: "para", text: paraLines.join(" ") });
  }
  return blocks;
}

function splitRow(line) {
  let t = line.trim();
  if (t.startsWith("|")) t = t.slice(1);
  if (t.endsWith("|")) t = t.slice(0, -1);
  return t.split("|").map(c => c.trim());
}

// ---------- Render blocks → docx children ----------
function renderBlocks(blocks) {
  const children = [];

  for (const b of blocks) {
    if (b.type === "heading") {
      const levels = [
        HeadingLevel.HEADING_1, HeadingLevel.HEADING_2, HeadingLevel.HEADING_3,
        HeadingLevel.HEADING_4, HeadingLevel.HEADING_5, HeadingLevel.HEADING_6,
      ];
      children.push(new Paragraph({
        heading: levels[Math.min(b.level, 6) - 1],
        children: parseInline(b.text),
        spacing: { before: 240, after: 120 },
      }));
    }
    else if (b.type === "para") {
      children.push(new Paragraph({
        children: parseInline(b.text),
        spacing: { before: 60, after: 120, line: 320 },
      }));
    }
    else if (b.type === "ul") {
      for (const it of b.items) {
        children.push(new Paragraph({
          numbering: { reference: "bullets", level: Math.min(it.level, 2) },
          children: parseInline(it.text),
          spacing: { before: 20, after: 60 },
        }));
      }
    }
    else if (b.type === "ol") {
      for (const it of b.items) {
        children.push(new Paragraph({
          numbering: { reference: "numbers", level: Math.min(it.level, 2) },
          children: parseInline(it.text),
          spacing: { before: 20, after: 60 },
        }));
      }
    }
    else if (b.type === "code") {
      // Code block: one paragraph per line, monospace, light gray background
      for (const l of (b.lines.length ? b.lines : [""])) {
        children.push(new Paragraph({
          children: [new TextRun({ text: l || " ", font: MONO, size: 20 })],
          spacing: { before: 0, after: 0, line: 280 },
          shading: { type: ShadingType.CLEAR, fill: "F6F8FA" },
        }));
      }
      // Trailing spacer
      children.push(new Paragraph({ children: [new TextRun("")], spacing: { after: 120 } }));
    }
    else if (b.type === "quote") {
      children.push(new Paragraph({
        children: parseInline(b.text),
        indent: { left: 360 },
        border: { left: { style: BorderStyle.SINGLE, size: 24, color: "B0B0B0", space: 12 } },
        spacing: { before: 120, after: 120 },
      }));
    }
    else if (b.type === "hr") {
      children.push(new Paragraph({
        children: [new TextRun("")],
        border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: "B0B0B0", space: 1 } },
        spacing: { before: 120, after: 120 },
      }));
    }
    else if (b.type === "table") {
      children.push(renderTable(b));
      children.push(new Paragraph({ children: [new TextRun("")], spacing: { after: 120 } }));
    }
  }
  return children;
}

function renderTable({ header, rows }) {
  const colCount = header.length;
  // Total content width on A4 with 1" margins ≈ 9026 DXA
  const totalWidth = 9000;
  const colWidth = Math.floor(totalWidth / colCount);
  const columnWidths = new Array(colCount).fill(colWidth);
  // Fix rounding
  columnWidths[colCount - 1] += totalWidth - colWidth * colCount;

  const border = { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF" };
  const borders = { top: border, bottom: border, left: border, right: border };
  const cellMargins = { top: 60, bottom: 60, left: 100, right: 100 };

  const makeCell = (text, widthIdx, isHeader) => new TableCell({
    borders,
    width: { size: columnWidths[widthIdx], type: WidthType.DXA },
    margins: cellMargins,
    shading: isHeader ? { type: ShadingType.CLEAR, fill: "E7EEF7" } : undefined,
    children: [new Paragraph({
      children: parseInline(text).map(r => {
        // Force bold for header cells
        if (isHeader) {
          return new TextRun({ text: r.options?.text ?? "", font: r.options?.font ?? FONT, bold: true });
        }
        return r;
      }),
      spacing: { before: 0, after: 0 },
    })],
  });

  const tableRows = [
    new TableRow({
      tableHeader: true,
      children: header.map((h, idx) => makeCell(h, idx, true)),
    }),
    ...rows.map(r => new TableRow({
      children: r.map((c, idx) => makeCell(c ?? "", idx, false)),
    })),
  ];

  return new Table({
    width: { size: totalWidth, type: WidthType.DXA },
    columnWidths,
    rows: tableRows,
  });
}

// ---------- Build document ----------
const md = fs.readFileSync(inputPath, "utf8");
const blocks = parseMarkdown(md);
const body = renderBlocks(blocks);

const doc = new Document({
  styles: {
    default: {
      document: { run: { font: FONT, size: 22 } }, // 11pt
    },
    paragraphStyles: [
      {
        id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 36, bold: true, font: FONT, color: "1F3864" },
        paragraph: { spacing: { before: 360, after: 180 }, outlineLevel: 0 },
      },
      {
        id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 30, bold: true, font: FONT, color: "2E75B6" },
        paragraph: { spacing: { before: 280, after: 140 }, outlineLevel: 1 },
      },
      {
        id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 26, bold: true, font: FONT, color: "2E75B6" },
        paragraph: { spacing: { before: 220, after: 110 }, outlineLevel: 2 },
      },
      {
        id: "Heading4", name: "Heading 4", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 24, bold: true, font: FONT, color: "404040" },
        paragraph: { spacing: { before: 180, after: 90 }, outlineLevel: 3 },
      },
    ],
  },
  numbering: {
    config: [
      {
        reference: "bullets",
        levels: [
          { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 420, hanging: 240 } } } },
          { level: 1, format: LevelFormat.BULLET, text: "◦", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 840, hanging: 240 } } } },
          { level: 2, format: LevelFormat.BULLET, text: "▪", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 1260, hanging: 240 } } } },
        ],
      },
      {
        reference: "numbers",
        levels: [
          { level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 420, hanging: 300 } } } },
          { level: 1, format: LevelFormat.DECIMAL, text: "%2.", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 840, hanging: 300 } } } },
          { level: 2, format: LevelFormat.DECIMAL, text: "%3.", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 1260, hanging: 300 } } } },
        ],
      },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 }, // A4
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
      },
    },
    children: body,
  }],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(outputPath, buf);
  console.log("✔ wrote", outputPath, "(", buf.length, "bytes )");
}).catch(e => {
  console.error("✗ failed:", e);
  process.exit(1);
});
