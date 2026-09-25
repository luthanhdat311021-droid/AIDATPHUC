/**
 * Client-side file ingestion for the import screen.
 * Text is extracted in the browser whenever possible so large documents never hit Vercel's 4.5MB request limit;
 * only images and scanned PDFs are uploaded as binaries (images are downscaled first).
 */

export type FileKind = 'doc' | 'image';

// Vercel rejects request bodies above 4.5MB; keep binary uploads safely below it
export const MAX_BINARY_UPLOAD_BYTES = 4 * 1024 * 1024;
export const MAX_TEXT_SOURCE_BYTES = 50 * 1024 * 1024;

export const DOC_EXTENSIONS = ['pdf', 'docx', 'pptx', 'txt', 'md', 'csv', 'json'];
export const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp'];

export const ACCEPT_BY_KIND: Record<FileKind, string> = {
  doc: DOC_EXTENSIONS.map(e => `.${e}`).join(','),
  image: IMAGE_EXTENSIONS.map(e => `.${e}`).join(',') + ',image/*'
};

export const extOf = (name: string) => (name.split('.').pop() || '').toLowerCase();

/**
 * Decide whether a file can be imported; returns its kind or a Vietnamese error message
 */
export function classifyFile(file: File): { kind: FileKind } | { error: string } {
  const ext = extOf(file.name);
  if (ext === 'doc' || ext === 'ppt') {
    return { error: `Định dạng .${ext} (Office cũ) chưa được hỗ trợ. Hãy lưu lại thành .${ext}x hoặc PDF rồi thử lại.` };
  }
  if (ext === 'heic' || ext === 'heif') {
    return { error: 'Ảnh HEIC chưa được hỗ trợ. Hãy chuyển sang JPG/PNG (hoặc chụp màn hình ảnh) rồi thử lại.' };
  }
  const isImage = IMAGE_EXTENSIONS.includes(ext) || (file.type.startsWith('image/') && !ext);
  if (isImage) return { kind: 'image' };
  if (DOC_EXTENSIONS.includes(ext)) {
    if (file.size > MAX_TEXT_SOURCE_BYTES) {
      return { error: `Tệp "${file.name}" quá lớn (${formatSize(file.size)}). Giới hạn là 50MB.` };
    }
    return { kind: 'doc' };
  }
  return { error: `Không hỗ trợ tệp "${file.name}". Hãy dùng PDF, DOCX, PPTX, TXT hoặc ảnh JPG/PNG/WEBP.` };
}

export function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Downscale and re-encode an image so it fits the upload limit while keeping text legible for AI reading.
 * EXIF orientation is applied by the browser when decoding.
 */
export async function compressImage(file: File): Promise<File> {
  const small = file.size <= 1.5 * 1024 * 1024 && ['jpg', 'jpeg', 'png', 'webp'].includes(extOf(file.name));
  if (small) return file;

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' } as ImageBitmapOptions);
  } catch {
    throw new Error('Không đọc được ảnh này. Hãy thử ảnh JPG hoặc PNG khác.');
  }

  const baseName = file.name.replace(/\.[^/.]+$/, '') || 'anh';
  // Long edge 2400px keeps small print readable; step down only if the result is still too heavy
  for (const maxEdge of [2400, 2000, 1600]) {
    const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    const ctx = canvas.getContext('2d');
    if (!ctx) break;
    ctx.fillStyle = '#ffffff'; // transparent PNG areas become white instead of black in JPEG
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);

    for (const quality of [0.88, 0.8, 0.7]) {
      const blob = await new Promise<Blob | null>(res => canvas.toBlob(res, 'image/jpeg', quality));
      if (blob && blob.size <= MAX_BINARY_UPLOAD_BYTES) {
        bitmap.close();
        return new File([blob], `${baseName}.jpg`, { type: 'image/jpeg' });
      }
    }
  }
  bitmap.close();
  throw new Error('Ảnh quá lớn, không thể nén xuống dưới 4MB. Hãy cắt bớt hoặc chụp lại với độ phân giải thấp hơn.');
}

/**
 * Extract text from a PDF with PDF.js. Returns '' for scanned PDFs (no text layer).
 */
export async function extractPdfText(file: File): Promise<string> {
  const pdfjsLib = await import('pdfjs-dist');
  // Worker is bundled by Vite so its version always matches the library
  const workerUrl = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default;
  pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl;

  const pdfDoc = await pdfjsLib.getDocument({ data: new Uint8Array(await file.arrayBuffer()) }).promise;
  const pages: string[] = [];
  for (let i = 1; i <= pdfDoc.numPages; i++) {
    const page = await pdfDoc.getPage(i);
    const content = await page.getTextContent();
    // Respect line breaks PDF.js reports so headings and list items stay separated
    const text = content.items
      .map((item: any) => (item.str || '') + (item.hasEOL ? '\n' : ' '))
      .join('')
      .replace(/[ \t]+\n/g, '\n')
      .trim();
    if (text) pages.push(text);
  }
  await pdfDoc.destroy();
  return pages.join('\n\n');
}

/**
 * Extract slide text from a PPTX (a zip of slide XML files), in slide order.
 */
export async function extractPptxText(file: File): Promise<string> {
  const { default: JSZip } = await import('jszip');
  const zip = await JSZip.loadAsync(await file.arrayBuffer());
  const slidePaths = Object.keys(zip.files)
    .filter(p => /^ppt\/slides\/slide\d+\.xml$/.test(p))
    .sort((a, b) => Number(a.match(/\d+/g)!.pop()) - Number(b.match(/\d+/g)!.pop()));

  const parser = new DOMParser();
  const slides: string[] = [];
  for (const [idx, p] of slidePaths.entries()) {
    const xml = parser.parseFromString(await zip.file(p)!.async('string'), 'application/xml');
    // Each <a:p> is a paragraph made of <a:t> runs
    const paragraphs = Array.from(xml.getElementsByTagName('a:p'))
      .map(para => Array.from(para.getElementsByTagName('a:t')).map(t => t.textContent || '').join(''))
      .map(s => s.trim())
      .filter(Boolean);
    if (paragraphs.length) slides.push(`Slide ${idx + 1}:\n${paragraphs.join('\n')}`);
  }
  return slides.join('\n\n');
}

export async function extractDocxText(file: File): Promise<string> {
  const mammoth = (await import('mammoth')).default;
  const result = await mammoth.extractRawText({ arrayBuffer: await file.arrayBuffer() });
  return (result.value || '').trim();
}
