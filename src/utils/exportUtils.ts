import { Flashcard, AIQuiz, AINotes } from '../types';

// Helper to trigger browser file download
function downloadFile(content: string, fileName: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// 1. Export Flashcards to Anki / Quizlet compatible TSV/CSV
export function exportFlashcardsToAnki(flashcards: Flashcard[], deckName: string = 'StudyMind') {
  if (!flashcards || flashcards.length === 0) return false;

  // Header and rows (Anki prefers Tab Separated with HTML support)
  const rows = flashcards.map((c) => {
    const front = `"${(c.front || '').replace(/"/g, '""')}"`;
    const back = `"${(c.back || '').replace(/"/g, '""')}"`;
    const tag = `"${deckName.replace(/\s+/g, '_')}_${c.difficulty || 'normal'}"`;
    return `${front}\t${back}\t${tag}`;
  });

  const content = `#separator:tab\n#html:true\n#deck:${deckName}\n#tags column:3\n` + rows.join('\n');
  const safeName = deckName.replace(/[^a-zA-Z0-9_\u00C0-\u024F\u1EA0-\u1EF9]/g, '_').slice(0, 30);
  downloadFile(content, `${safeName}_anki_deck.txt`, 'text/tab-separated-values;charset=utf-8');
  return true;
}

// 2. Export Notes to Clean Markdown (.md)
export function exportNotesToMarkdown(notes: AINotes | null | undefined, docTitle: string = 'Tai_lieu') {
  const title = notes?.summaryTitle || docTitle;
  let md = `# ${title}\n\n`;
  md += `*Tài liệu học tập được tạo tự động bởi StudyMind AI*\n\n---\n\n`;

  if (notes?.sections && notes.sections.length > 0) {
    notes.sections.forEach((sec) => {
      md += `## ${sec.heading}\n\n`;
      sec.items?.forEach((item) => {
        md += `- **${item.label}**: ${item.text}\n`;
      });
      md += `\n`;
    });
  } else {
    md += `(Chưa có nội dung ghi chú tóm tắt)\n`;
  }

  const safeName = docTitle.replace(/[^a-zA-Z0-9_\u00C0-\u024F\u1EA0-\u1EF9]/g, '_').slice(0, 30);
  downloadFile(md, `${safeName}_ghi_chu.md`, 'text/markdown;charset=utf-8');
  return true;
}

// 3. Export Quiz to Printable PDF / A4 Test Sheet
export function exportQuizToPrintable(quiz: AIQuiz | null | undefined, docTitle: string = 'Đề thi trắc nghiệm') {
  if (!quiz || !quiz.questions || quiz.questions.length === 0) return false;

  const printWindow = window.open('', '_blank');
  if (!printWindow) return false;

  const html = `
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>${quiz.title || 'Đề kiểm tra'} - StudyMind AI</title>
  <style>
    @page { size: A4; margin: 20mm; }
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; font-size: 13pt; line-height: 1.5; color: #111; margin: 0; padding: 20px; }
    .header { text-align: center; border-bottom: 2px solid #333; padding-bottom: 12px; margin-bottom: 20px; }
    .header h1 { margin: 0 0 6px; font-size: 18pt; text-transform: uppercase; }
    .header p { margin: 2px 0; font-size: 11pt; color: #555; }
    .meta-box { display: flex; justify-content: space-between; border: 1px dashed #666; padding: 10px; margin-bottom: 20px; font-size: 11pt; }
    .question { margin-bottom: 18px; page-break-inside: avoid; }
    .q-text { font-weight: bold; margin-bottom: 6px; }
    .options { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; padding-left: 14px; }
    .opt { font-size: 12pt; }
    .page-break { page-break-before: always; margin-top: 40px; }
    .key-table { width: 100%; border-collapse: collapse; margin-top: 15px; }
    .key-table th, .key-table td { border: 1px solid #444; padding: 8px; text-align: left; font-size: 11pt; }
    .key-table th { background-color: #f2f2f2; }
    .print-btn-bar { text-align: center; margin-bottom: 20px; }
    .btn { background: #0F766E; color: white; border: none; padding: 10px 24px; font-size: 14pt; font-weight: bold; border-radius: 8px; cursor: pointer; }
    @media print { .print-btn-bar { display: none; } }
  </style>
</head>
<body>
  <div class="print-btn-bar">
    <button class="btn" onclick="window.print()">🖨️ In Đề Thi hoặc Lưu dạng PDF</button>
  </div>

  <div class="header">
    <h1>${quiz.title || 'ĐỀ KIỂM TRA TRẮC NGHIỆM'}</h1>
    <p>Chuyên đề / Tài liệu: <strong>${docTitle}</strong></p>
    <p>Thời gian làm bài: ${quiz.timeLimitMinutes || 15} phút (Không kể thời gian phát đề)</p>
  </div>

  <div class="meta-box">
    <div>Họ và tên thí sinh: ....................................................................</div>
    <div>Lớp: ....................... Điểm: .......... / 10</div>
  </div>

  <div class="questions-list">
    ${quiz.questions.map((q, idx) => `
      <div class="question">
        <div class="q-text">Câu ${idx + 1}: ${q.questionText}</div>
        <div class="options">
          ${q.options.map(opt => `<div class="opt">${opt}</div>`).join('')}
        </div>
      </div>
    `).join('')}
  </div>

  <div class="page-break"></div>

  <div class="header">
    <h2>ĐÁP ÁN & LỜI GIẢI CHI TIẾT</h2>
    <p>Tài liệu: ${docTitle}</p>
  </div>

  <table class="key-table">
    <thead>
      <tr>
        <th style="width: 10%;">Câu</th>
        <th style="width: 15%;">Đáp án</th>
        <th>Giải thích chi tiết</th>
      </tr>
    </thead>
    <tbody>
      ${quiz.questions.map((q, idx) => {
        const letters = ['A', 'B', 'C', 'D'];
        const correctLetter = letters[q.correctIndex] || 'A';
        return `
          <tr>
            <td style="text-align: center; font-weight: bold;">${idx + 1}</td>
            <td style="text-align: center; font-weight: bold; color: #0F766E;">${correctLetter}</td>
            <td>${q.explanation || 'Không có giải thích chi tiết.'}</td>
          </tr>
        `;
      }).join('')}
    </tbody>
  </table>

  <script>
    window.onload = function() {
      // Auto-trigger print prompt after short delay
      setTimeout(() => { window.print(); }, 400);
    };
  </script>
</body>
</html>
  `;

  printWindow.document.write(html);
  printWindow.document.close();
  return true;
}
