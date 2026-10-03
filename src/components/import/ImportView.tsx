import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  FileText,
  Image as ImageIcon,
  Video,
  Link as LinkIcon,
  Edit3,
  UploadCloud,
  Loader2,
  X,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';
import { Capacitor } from '@capacitor/core';
import { useStudy } from '../../context/StudyContext';
import { OutputOptions } from '../../types';
import {
  FileKind,
  ACCEPT_BY_KIND,
  MAX_BINARY_UPLOAD_BYTES,
  classifyFile,
  compressImage,
  extOf,
  extractDocxText,
  extractPdfText,
  extractPptxText,
  formatSize
} from '../../utils/fileIngest';

type SourceTab = 'doc' | 'image' | 'video' | 'url' | 'text';
type ProcessStatus = { state: 'idle' | 'working' | 'done' | 'error'; message: string };

export function ImportView() {
  const { uploadDocument, processVideo, processUrl, showToast } = useStudy();

  const [activeSourceTab, setActiveSourceTab] = useState<SourceTab>('doc');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [videoUrlInput, setVideoUrlInput] = useState<string>('');
  const [webUrlInput, setWebUrlInput] = useState<string>('');
  const [rawTextInput, setRawTextInput] = useState<string>('');

  const [language, setLanguage] = useState<string>('Tiếng Việt (Mặc định)');
  const [depth, setDepth] = useState<string>('Tiêu chuẩn');

  const [outputOptions, setOutputOptions] = useState<OutputOptions>({
    notes: true,
    mindmap: true,
    flashcards: true,
    quiz: true
  });

  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [progressPercent, setProgressPercent] = useState<number>(0);
  const [status, setStatus] = useState<ProcessStatus>({ state: 'idle', message: '' });
  const [isDragging, setIsDragging] = useState<boolean>(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const dragDepth = useRef(0);

  const [deviceType, setDeviceType] = useState<'mobile' | 'tablet' | 'desktop'>('desktop');

  useEffect(() => {
    const detectDevice = () => {
      try {
        if (Capacitor.isNativePlatform()) {
          const platform = Capacitor.getPlatform();
          if (platform === 'android' || platform === 'ios') {
            setDeviceType('mobile');
            return;
          }
        }
      } catch {
        // fallback
      }

      if (typeof window !== 'undefined') {
        const ua = navigator.userAgent || navigator.vendor || (window as any).opera || '';
        if (/(ipad|tablet|(android(?!.*mobile))|(windows(?!.*phone)(.*touch))|kindle|playbook|silk|(puffin(?!.*(IP|AP|WP))))/i.test(ua)) {
          setDeviceType('tablet');
        } else if (/Android|webOS|iPhone|iPod|BlackBerry|IEMobile|Opera Mini/i.test(ua) || (window.innerWidth <= 768 && 'ontouchstart' in window)) {
          setDeviceType('mobile');
        } else {
          setDeviceType('desktop');
        }
      }
    };

    detectDevice();
    window.addEventListener('resize', detectDevice);
    return () => window.removeEventListener('resize', detectDevice);
  }, []);

  // Image thumbnail for the drop zone
  useEffect(() => {
    if (!selectedFile || !selectedFile.type.startsWith('image/')) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(selectedFile);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [selectedFile]);

  const fileButtonText = deviceType === 'mobile'
    ? 'Chọn tệp tin từ điện thoại'
    : deviceType === 'tablet'
      ? 'Chọn tệp tin từ máy tính bảng'
      : 'Chọn tệp tin từ máy tính';

  const isFileTab = activeSourceTab === 'doc' || activeSourceTab === 'image';
  const fileKind: FileKind = activeSourceTab === 'image' ? 'image' : 'doc';

  const sourceTabs = [
    { id: 'doc', label: 'PDF / Word / PPT', icon: FileText },
    { id: 'image', label: 'Ảnh / Ảnh chụp sách', icon: ImageIcon },
    { id: 'video', label: 'Video / YouTube Link', icon: Video },
    { id: 'url', label: 'Liên kết Web / Bài báo', icon: LinkIcon },
    { id: 'text', label: 'Nhập Văn bản thô', icon: Edit3 },
  ];

  // Single entry point for picked, dropped and pasted files: validate, then switch to the matching tab
  const acceptFiles = useCallback((files: FileList | File[] | null | undefined) => {
    const list = Array.from(files || []);
    if (list.length === 0) return;
    if (isProcessing) {
      showToast("Đang xử lý tài liệu, vui lòng đợi xong rồi thêm tệp mới.");
      return;
    }
    const file = list[0];
    const result = classifyFile(file);
    if ('error' in result) {
      showToast(result.error);
      return;
    }
    if (list.length > 1) showToast(`Mỗi lần chỉ xử lý 1 tệp — đã chọn "${file.name}".`);
    setSelectedFile(file);
    setActiveSourceTab(result.kind);
    setStatus({ state: 'idle', message: '' });
    setProgressPercent(0);
  }, [isProcessing, showToast]);

  // Page-wide drag & drop: stop the browser from opening a dropped file and leaving the app,
  // and accept the drop anywhere on the import screen.
  useEffect(() => {
    const hasFiles = (e: DragEvent) => Array.from(e.dataTransfer?.types || []).includes('Files');

    const onDragEnter = (e: DragEvent) => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      dragDepth.current += 1;
      setIsDragging(true);
    };
    const onDragOver = (e: DragEvent) => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
    };
    const onDragLeave = (e: DragEvent) => {
      if (!hasFiles(e)) return;
      dragDepth.current = Math.max(0, dragDepth.current - 1);
      if (dragDepth.current === 0) setIsDragging(false);
    };
    const onDrop = (e: DragEvent) => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      dragDepth.current = 0;
      setIsDragging(false);
      acceptFiles(e.dataTransfer?.files);
    };
    // Ctrl+V a screenshot or copied file (text pastes into inputs are left alone)
    const onPaste = (e: ClipboardEvent) => {
      const files = Array.from(e.clipboardData?.files || []);
      if (files.length === 0) return;
      // Copying from Word also puts an image of the text on the clipboard — keep text pastes into fields as text
      const target = e.target as HTMLElement | null;
      const isField = !!target && (target.tagName === 'TEXTAREA' || target.tagName === 'INPUT' || target.isContentEditable);
      if (isField && e.clipboardData?.types.includes('text/plain')) return;
      e.preventDefault();
      acceptFiles(files);
    };

    window.addEventListener('dragenter', onDragEnter);
    window.addEventListener('dragover', onDragOver);
    window.addEventListener('dragleave', onDragLeave);
    window.addEventListener('drop', onDrop);
    window.addEventListener('paste', onPaste);
    return () => {
      window.removeEventListener('dragenter', onDragEnter);
      window.removeEventListener('dragover', onDragOver);
      window.removeEventListener('dragleave', onDragLeave);
      window.removeEventListener('drop', onDrop);
      window.removeEventListener('paste', onPaste);
    };
  }, [acceptFiles]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    acceptFiles(e.target.files);
    // Reset so picking the same file again still fires onChange
    e.target.value = '';
  };

  const openFilePicker = () => {
    if (!isProcessing) fileInputRef.current?.click();
  };

  const clearFile = () => {
    setSelectedFile(null);
    setStatus({ state: 'idle', message: '' });
    setProgressPercent(0);
  };

  // Turn the selected file into either extracted text or a binary that fits the upload limit
  const prepareFile = async (file: File): Promise<{ text?: string; binary?: File }> => {
    const ext = extOf(file.name);

    if (file.type.startsWith('image/') || ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp'].includes(ext)) {
      setStatus({ state: 'working', message: 'Đang tối ưu ảnh để tải lên...' });
      return { binary: await compressImage(file) };
    }

    setStatus({ state: 'working', message: 'Đang đọc nội dung tệp...' });
    let text = '';
    if (ext === 'docx') text = await extractDocxText(file);
    else if (ext === 'pptx') text = await extractPptxText(file);
    else if (['txt', 'md', 'csv', 'json'].includes(ext)) text = (await file.text()).trim();
    else if (ext === 'pdf') {
      try {
        text = await extractPdfText(file);
      } catch (err) {
        console.warn("PDF.js extraction failed, uploading the file instead:", err);
      }
    }

    if (text.replace(/\s/g, '').length >= 30) return { text };

    // No text layer (scanned PDF) or unreadable: let the server's AI read the file itself
    if (file.size > MAX_BINARY_UPLOAD_BYTES) {
      throw new Error(
        ext === 'pdf'
          ? `PDF "${file.name}" là bản scan (không có lớp chữ) và nặng ${formatSize(file.size)}, vượt giới hạn 4MB. Hãy tách thành file nhỏ hơn hoặc chụp ảnh từng trang.`
          : `Không đọc được nội dung "${file.name}".`
      );
    }
    if (ext === 'pdf') setStatus({ state: 'working', message: 'PDF dạng scan — AI sẽ đọc trực tiếp nội dung...' });
    return { binary: file };
  };

  const handleStartProcessing = async () => {
    if (activeSourceTab === 'video' && !videoUrlInput.trim()) {
      showToast("Vui lòng nhập đường dẫn Video / YouTube!");
      return;
    }
    if (activeSourceTab === 'url' && !webUrlInput.trim()) {
      showToast("Vui lòng nhập liên kết Web!");
      return;
    }
    if (activeSourceTab === 'text' && !rawTextInput.trim()) {
      showToast("Vui lòng dán hoặc nhập nội dung văn bản!");
      return;
    }
    if (isFileTab && !selectedFile) {
      showToast("Vui lòng chọn hoặc kéo thả tệp trước khi bấm chuyển hóa!");
      return;
    }

    setIsProcessing(true);
    setProgressPercent(5);
    setStatus({ state: 'working', message: 'Đang chuẩn bị...' });

    // Creep toward 90% while the server works; the real completion sets 100%
    const interval = setInterval(() => {
      setProgressPercent((prev) => (prev >= 90 ? 90 : prev + Math.max(1, Math.round((90 - prev) / 12))));
    }, 700);

    try {
      let result: any;
      if (activeSourceTab === 'video') {
        result = await processVideo(videoUrlInput);
      } else if (activeSourceTab === 'url') {
        result = await processUrl(webUrlInput);
      } else if (activeSourceTab === 'text') {
        setStatus({ state: 'working', message: 'AI đang phân tích & trích xuất kiến thức...' });
        result = await uploadDocument(null, language, depth, outputOptions, rawTextInput);
      } else if (selectedFile) {
        const prepared = await prepareFile(selectedFile);
        setStatus({ state: 'working', message: 'AI đang phân tích & trích xuất kiến thức...' });
        result = prepared.text
          ? await uploadDocument(null, language, depth, outputOptions, prepared.text, selectedFile.name)
          : await uploadDocument(prepared.binary!, language, depth, outputOptions);
      }

      // uploadDocument shows its own toast and returns { success: false } / undefined on failure
      // (processVideo / processUrl always resolve to undefined and report errors via toast)
      const usedUpload = activeSourceTab === 'text' || isFileTab;
      if ((usedUpload && !result) || (result && result.success === false)) {
        setStatus({ state: 'error', message: result.error || 'Xử lý thất bại, vui lòng thử lại.' });
        setProgressPercent(0);
      } else {
        setStatus({ state: 'done', message: 'Đã hoàn tất trích xuất kiến thức' });
        setProgressPercent(100);
      }
    } catch (err: any) {
      const message = err?.message || 'Không thể xử lý tệp này.';
      setStatus({ state: 'error', message });
      setProgressPercent(0);
      showToast(message);
    } finally {
      clearInterval(interval);
      setIsProcessing(false);
    }
  };

  const showStatusCard = (isFileTab && selectedFile) || status.state !== 'idle';

  return (
    <div className="relative p-4 md:p-8 space-y-6 max-w-6xl mx-auto">
      {/* Full-screen drop overlay while dragging a file anywhere over the page */}
      {isDragging && (
        <div className="fixed inset-0 z-[60] bg-teal-900/40 backdrop-blur-xs flex items-center justify-center pointer-events-none">
          <div className="bg-white rounded-2xl border-2 border-dashed border-[#0F766E] px-10 py-8 text-center shadow-2xl">
            <UploadCloud className="w-12 h-12 text-[#0F766E] mx-auto mb-2" />
            <p className="font-extrabold text-base text-[#111827]">Thả tệp để tải lên</p>
            <p className="text-xs text-slate-500 mt-1">PDF, DOCX, PPTX, TXT hoặc ảnh JPG/PNG/WEBP</p>
          </div>
        </div>
      )}

      {/* Title Header */}
      <div className="space-y-1">
        <h2 className="text-xl md:text-2xl font-bold text-[#111827]">
          Nhập tài liệu học tập
        </h2>
        <p className="text-xs md:text-sm text-slate-500">
          Tải lên bất kỳ tài liệu nào để bắt đầu quá trình trích xuất kiến thức bằng AI
        </p>
      </div>

      {/* Top Source Tabs Pills */}
      <div className="flex items-center gap-2 overflow-x-auto pb-2 border-b border-slate-200 scrollbar-none">
        {sourceTabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeSourceTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveSourceTab(tab.id as SourceTab)}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-lg text-xs md:text-sm font-semibold transition-all shrink-0 ${
                isActive
                  ? 'bg-[#CCFBF1] text-[#0F766E] border border-[#0F766E] shadow-xs'
                  : 'bg-white text-slate-600 hover:bg-slate-50 border border-slate-200'
              }`}
            >
              <Icon className="w-4 h-4" />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Main Form Content (Grid 2 cols) */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        {/* Left Column: Upload / Input Zone (3 cols) */}
        <div className="lg:col-span-3 space-y-5">
          {isFileTab ? (
            /* Drag & Drop Zone — the whole zone is clickable and keyboard accessible */
            <div
              role="button"
              tabIndex={0}
              onClick={openFilePicker}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  openFilePicker();
                }
              }}
              className={`border-2 border-dashed transition-all rounded-2xl p-8 text-center space-y-4 cursor-pointer outline-none focus-visible:ring-4 focus-visible:ring-teal-200 ${
                isDragging
                  ? 'border-[#0F766E] bg-teal-50 scale-[1.01]'
                  : 'bg-white border-teal-600/40 hover:border-[#0F766E] hover:bg-teal-50/30'
              } ${isProcessing ? 'opacity-60 cursor-not-allowed' : ''}`}
            >
              {previewUrl ? (
                <img src={previewUrl} alt={selectedFile?.name} className="max-h-48 mx-auto rounded-xl border border-slate-200 shadow-sm object-contain" />
              ) : (
                <div className="w-16 h-16 bg-teal-50 text-[#0F766E] rounded-full flex items-center justify-center mx-auto shadow-inner">
                  {selectedFile ? <FileText className="w-8 h-8" /> : <UploadCloud className="w-8 h-8" />}
                </div>
              )}
              <div className="space-y-1">
                <h4 className="font-bold text-base text-[#111827] break-all">
                  {selectedFile
                    ? selectedFile.name
                    : deviceType === 'desktop'
                      ? (activeSourceTab === 'image' ? 'Kéo thả ảnh vào đây hoặc dán (Ctrl+V)' : 'Kéo và thả tệp tin của bạn vào đây')
                      : 'Chạm để chọn tệp tin'}
                </h4>
                <p className="text-xs text-slate-500">
                  {selectedFile
                    ? `${formatSize(selectedFile.size)} • Bấm để chọn tệp khác`
                    : activeSourceTab === 'image'
                      ? 'JPG, PNG, WEBP — ảnh lớn được tự động nén'
                      : 'PDF, DOCX, PPTX, TXT, MD — tối đa 50MB (PDF scan tối đa 4MB)'}
                </p>
              </div>

              <div>
                <span className="inline-block bg-[#0F766E] hover:bg-[#0D5C53] text-white font-bold text-xs px-5 py-2.5 rounded-lg shadow-sm transition-all">
                  {selectedFile ? 'Đổi tệp khác' : fileButtonText}
                </span>
              </div>
              <input
                ref={fileInputRef}
                type="file"
                onChange={handleFileChange}
                accept={ACCEPT_BY_KIND[fileKind]}
                className="hidden"
              />
            </div>
          ) : activeSourceTab === 'video' ? (
            /* Video / YouTube URL Input */
            <div className="bg-white border border-slate-200 rounded-2xl p-6 space-y-3">
              <label className="block text-xs font-bold text-[#111827]">
                Nhập liên kết Video / YouTube hoặc tải video bài giảng:
              </label>
              <input
                type="url"
                value={videoUrlInput}
                onChange={(e) => setVideoUrlInput(e.target.value)}
                placeholder="https://www.youtube.com/watch?v=..."
                className="w-full bg-slate-50 border border-slate-200 rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:border-[#0F766E] focus:ring-1 focus:ring-[#0F766E]"
              />
              <p className="text-[11px] text-slate-400">
                Hệ thống sẽ tự động tách âm thanh và chuyển thành Speech-to-Text chuẩn xác.
              </p>
            </div>
          ) : activeSourceTab === 'url' ? (
            /* Web URL Input */
            <div className="bg-white border border-slate-200 rounded-2xl p-6 space-y-3">
              <label className="block text-xs font-bold text-[#111827]">
                Nhập URL Bài báo / Trang web nghiên cứu:
              </label>
              <input
                type="url"
                value={webUrlInput}
                onChange={(e) => setWebUrlInput(e.target.value)}
                placeholder="https://wikipedia.org/wiki/Mitochondrion"
                className="w-full bg-slate-50 border border-slate-200 rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:border-[#0F766E] focus:ring-1 focus:ring-[#0F766E]"
              />
            </div>
          ) : (
            /* Raw Text Area */
            <div className="bg-white border border-slate-200 rounded-2xl p-6 space-y-3">
              <label className="block text-xs font-bold text-[#111827]">
                Dán nội dung văn bản thô:
              </label>
              <textarea
                rows={6}
                value={rawTextInput}
                onChange={(e) => setRawTextInput(e.target.value)}
                placeholder="Dán bài giảng hoặc ghi chú của bạn vào đây..."
                className="w-full bg-slate-50 border border-slate-200 rounded-lg p-3 text-sm focus:outline-none focus:border-[#0F766E] focus:ring-1 focus:ring-[#0F766E]"
              />
            </div>
          )}

          {/* Selected file / processing status */}
          {showStatusCard && (
            <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-2">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 overflow-hidden">
                  <div className="w-9 h-9 rounded-lg bg-teal-50 text-[#0F766E] flex items-center justify-center font-bold text-[10px] uppercase shrink-0">
                    {selectedFile && isFileTab ? extOf(selectedFile.name).slice(0, 4) : <FileText className="w-4 h-4" />}
                  </div>
                  <div className="overflow-hidden">
                    <h5 className="font-semibold text-xs md:text-sm text-[#111827] truncate">
                      {selectedFile && isFileTab ? selectedFile.name : 'Tài liệu đang xử lý'}
                    </h5>
                    <p className={`text-[11px] truncate flex items-center gap-1 ${
                      status.state === 'error' ? 'text-rose-600' : status.state === 'done' ? 'text-emerald-600' : 'text-slate-400'
                    }`}>
                      {status.state === 'done' && <CheckCircle2 className="w-3 h-3 shrink-0" />}
                      {status.state === 'error' && <AlertCircle className="w-3 h-3 shrink-0" />}
                      <span className="truncate">
                        {selectedFile && isFileTab ? `${formatSize(selectedFile.size)} • ` : ''}
                        {status.message || 'Sẵn sàng chuyển hóa'}
                      </span>
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  {status.state !== 'idle' && <span className="text-xs font-bold text-[#0F766E]">{progressPercent}%</span>}
                  {selectedFile && isFileTab && !isProcessing && (
                    <button onClick={clearFile} className="text-slate-400 hover:text-rose-500" title="Bỏ chọn tệp">
                      <X className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </div>

              {status.state !== 'idle' && (
                <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${status.state === 'error' ? 'bg-rose-400' : 'bg-[#0F766E]'}`}
                    style={{ width: `${status.state === 'error' ? 100 : progressPercent}%` }}
                  />
                </div>
              )}
            </div>
          )}
        </div>

        {/* Right Column: AI Output Configuration (2 cols) */}
        <div className="lg:col-span-2 bg-white border border-slate-200 rounded-2xl p-6 space-y-6 flex flex-col justify-between">
          <div className="space-y-5">
            <h3 className="font-bold text-base text-[#111827] border-b border-slate-100 pb-3">
              Cấu hình đầu ra AI
            </h3>

            {/* Language Selector */}
            <div className="space-y-2">
              <label className="block text-xs font-semibold text-slate-700">
                Ngôn ngữ kết quả
              </label>
              <select
                value={language}
                onChange={(e) => setLanguage(e.target.value)}
                className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2 text-xs md:text-sm text-[#111827] focus:outline-none focus:border-[#0F766E]"
              >
                <option>Tiếng Việt (Mặc định)</option>
                <option>Tiếng Anh (English)</option>
                <option>Song ngữ (Việt - Anh)</option>
              </select>
            </div>

            {/* Output Products Checkboxes */}
            <div className="space-y-3">
              <label className="block text-xs font-semibold text-slate-700">
                Sản phẩm muốn tạo
              </label>
              <div className="space-y-2.5">
                {[
                  { key: 'notes' as keyof OutputOptions, label: 'Ghi chú tóm tắt có cấu trúc' },
                  { key: 'mindmap' as keyof OutputOptions, label: 'Sơ đồ tư duy trực quan (Mindmap)' },
                  { key: 'flashcards' as keyof OutputOptions, label: 'Thẻ ghi nhớ thông minh (Flashcards)' },
                  { key: 'quiz' as keyof OutputOptions, label: 'Đề kiểm tra trắc nghiệm AI (Quiz)' },
                ].map((opt) => (
                  <label key={opt.key} className="flex items-center gap-2.5 cursor-pointer text-xs md:text-sm font-medium text-[#111827]">
                    <input
                      type="checkbox"
                      checked={outputOptions[opt.key]}
                      onChange={(e) => setOutputOptions({ ...outputOptions, [opt.key]: e.target.checked })}
                      className="w-4 h-4 rounded text-[#0F766E] focus:ring-[#0F766E]"
                    />
                    <span>{opt.label}</span>
                  </label>
                ))}
              </div>
            </div>

            {/* Detail Depth Pills */}
            <div className="space-y-2">
              <label className="block text-xs font-semibold text-slate-700">
                Độ sâu & Chi tiết
              </label>
              <div className="grid grid-cols-3 gap-2 bg-slate-50 p-1 rounded-lg border border-slate-200">
                {['Tóm lược nhanh', 'Tiêu chuẩn', 'Chuyên sâu'].map((item) => (
                  <button
                    key={item}
                    type="button"
                    onClick={() => setDepth(item)}
                    className={`py-1.5 text-[11px] font-bold rounded-md transition-all ${
                      depth === item
                        ? 'bg-[#CCFBF1] text-[#0F766E] border border-[#0F766E] shadow-2xs'
                        : 'text-slate-600 hover:bg-slate-200/50'
                    }`}
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Submit Action Button */}
          <div className="pt-4">
            <button
              onClick={handleStartProcessing}
              disabled={isProcessing}
              className="w-full bg-[#0F766E] hover:bg-[#0D5C53] text-white font-bold text-sm py-3 rounded-lg shadow-md transition-all hover:scale-101 flex items-center justify-center gap-2 disabled:opacity-50"
            >
              {isProcessing ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Đang xử lý tài liệu...</span>
                </>
              ) : (
                <span>Bắt đầu chuyển hóa tài liệu</span>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
