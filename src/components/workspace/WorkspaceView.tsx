import React, { useState, useEffect, useMemo } from 'react';
import { 
  ChevronLeft, 
  ChevronRight, 
  Share2, 
  Download, 
  Send, 
  Sparkles,
  RefreshCw,
  FileText,
  Compass
} from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { analyzeKnowledgeGaps } from '../../utils/knowledgeGaps';

type ChatMode = 'qa' | 'socratic';

interface ChatMessage {
  sender: 'user' | 'ai';
  text: string;
}

export function WorkspaceView() {
  const { 
    user,
    activeDocData, 
    setActiveTab, 
    sendChatMessage, 
    addFlashcard, 
    addMindmapNode, 
    addQuizQuestion,
    showToast 
  } = useStudy();
  const doc = activeDocData?.document;
  const pack = activeDocData?.studyPack;

  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [inputQuery, setInputQuery] = useState<string>('');
  const [isSending, setIsSending] = useState<boolean>(false);
  const [chatMode, setChatMode] = useState<ChatMode>('qa');

  // Root gaps from the Knowledge Gap Map steer the Socratic tutor's questions
  const gapRoots = useMemo(
    () => (pack ? analyzeKnowledgeGaps(pack, activeDocData?.quizHistory || []).roots.slice(0, 3) : []),
    [pack, activeDocData?.quizHistory]
  );
  const gapFocus = gapRoots.map(r => {
    const causes = r.explains.filter(n => n.id !== r.node.id).map(n => n.label);
    const wrongIdeas = r.misconceptions.map(m => `"${m.misconception}"`);
    return `- ${r.node.label}${r.node.level ? ` (${r.node.level})` : ''}`
      + (causes.length ? `: gây lỗi ở ${causes.join(', ')}` : '')
      + (wrongIdeas.length ? `; hiểu sai: ${wrongIdeas.join(', ')}` : '');
  }).join('\n');

  const greeting = (mode: ChatMode): ChatMessage => ({
    sender: 'ai',
    text: mode === 'socratic'
      ? `Chế độ Gia sư Socrates: mình sẽ không đưa đáp án, mà đặt câu hỏi để bạn tự suy luận ra. ${
          gapRoots[0] ? `Kết quả luyện tập cho thấy bạn nên củng cố "${gapRoots[0].node.label}". Theo bạn, phần này nói về điều gì?` : 'Bạn đang thắc mắc phần nào trong tài liệu?'
        }`
      : `Chào ${user.fullName}, tôi là trợ lý StudyMind AI. Tôi đã sẵn sàng hỗ trợ giải đáp thắc mắc và ôn luyện cùng bạn về tài liệu "${doc?.title}".`
  });

  // Each mode starts its own conversation, so Q&A answers never leak into a tutoring session
  const switchChatMode = (mode: ChatMode) => {
    setChatMode(mode);
    setChatMessages([greeting(mode)]);
  };

  // Sync welcome chat message when document changes
  useEffect(() => {
    if (doc) {
      setChatMode('qa');
      setChatMessages([greeting('qa')]);
    }
  }, [doc, user.fullName]);

  const handleSendChat = async (queryText?: string) => {
    const textToSend = queryText || inputQuery;
    if (!textToSend.trim()) return;

    const userMsg: ChatMessage = { sender: 'user', text: textToSend };
    setChatMessages((prev) => [...prev, userMsg]);
    setInputQuery('');
    setIsSending(true);

    const aiReply = await sendChatMessage(
      doc?.id || 'doc-1', textToSend, chatMessages,
      chatMode === 'socratic' ? { mode: 'socratic', focus: gapFocus } : undefined
    );
    
    setChatMessages((prev) => [...prev, { sender: 'ai', text: aiReply }]);
    setIsSending(false);
  };

  const handleQuickCreateFlashcard = async () => {
    const sampleFront = pack?.flashcards?.[0]?.front || `Khái niệm cốt lõi trong ${doc?.title || 'bài học'}?`;
    const sampleBack = pack?.flashcards?.[0]?.back || `Giải thích chi tiết kiến thức trọng tâm từ tài liệu.`;
    await addFlashcard({
      front: sampleFront,
      back: sampleBack,
      difficulty: "medium"
    });
    setActiveTab('flashcard');
  };

  const handleQuickCreateMindmap = async () => {
    const sampleLabel = pack?.mindmap?.nodes?.[0]?.label || doc?.title || "Chủ đề học tập";
    const sampleDetail = pack?.mindmap?.nodes?.[0]?.detail || "Chi tiết nội dung sơ đồ tư duy";
    await addMindmapNode({
      label: sampleLabel,
      detail: sampleDetail,
      subDetails: ["Khái niệm chính", "Ứng dụng thực tế"]
    });
    setActiveTab('mindmap');
  };

  const handleQuickCreateQuiz = async () => {
    const sampleQ = pack?.quiz?.questions?.[0] || {
      questionText: `Câu hỏi ôn tập kiến thức tài liệu ${doc?.title}?`,
      options: ["A. Phương án 1", "B. Phương án 2", "C. Phương án 3", "D. Phương án 4"],
      correctIndex: 0,
      explanation: "Giải thích chi tiết từ hệ thống AI."
    };
    await addQuizQuestion({
      questionText: sampleQ.questionText,
      options: sampleQ.options,
      correctIndex: sampleQ.correctIndex,
      explanation: sampleQ.explanation
    });
    setActiveTab('quiz');
  };

  // Process raw text into clean paragraphs
  const rawTextLines = (doc?.rawText || `1. Nội dung trích xuất từ tài liệu ${doc?.title || ''}\nĐang cập nhật dữ liệu tài liệu...`).split('\n').filter(Boolean);

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)] max-w-[1600px] mx-auto">
      {/* Top Document Header Bar */}
      <div className="bg-white border-b border-slate-200 px-4 py-2.5 flex items-center justify-between text-xs font-medium text-slate-600">
        <div className="flex items-center gap-2 overflow-hidden">
          <FileText className="w-4 h-4 text-[#0F766E] shrink-0" />
          <span className="truncate font-bold text-[#111827]">
            Nguồn: {doc?.title || 'Tài liệu học tập.pdf'}
          </span>
          <div className="flex items-center gap-1 bg-slate-100 px-2 py-0.5 rounded text-[11px] text-slate-500 shrink-0">
            <span>Trang 1/1</span>
            <button className="hover:text-black"><ChevronLeft className="w-3 h-3" /></button>
            <button className="hover:text-black"><ChevronRight className="w-3 h-3" /></button>
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button 
            onClick={() => showToast("🔗 Đã sao chép liên kết tài liệu vào bộ nhớ tạm!")} 
            className="p-1.5 hover:bg-slate-100 rounded text-slate-600" 
            title="Chia sẻ"
          >
            <Share2 className="w-4 h-4" />
          </button>
          <button 
            onClick={() => showToast("📥 Đã tải file tài liệu về máy!")} 
            className="p-1.5 hover:bg-slate-100 rounded text-slate-600" 
            title="Tải về"
          >
            <Download className="w-4 h-4" />
          </button>
          <button 
            onClick={() => showToast("Đã làm mới phân tích cho tài liệu!")} 
            className="p-1.5 hover:bg-slate-100 rounded text-slate-600" 
            title="Làm mới"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Readiness banner — entry point to the prerequisite analysis */}
      <button
        onClick={() => setActiveTab('prerequisite')}
        className="bg-amber-50 hover:bg-amber-100 border-b border-amber-200 px-4 py-2 flex items-center gap-2 text-left text-xs text-amber-900 transition-colors"
      >
        <Compass className="w-4 h-4 shrink-0 text-amber-600" />
        <span className="flex-1 min-w-0 truncate">
          {pack?.prerequisites?.prerequisites?.length
            ? <>Bài này cần <strong>{pack.prerequisites.prerequisites.filter(p => p.priority === 'critical').length} kiến thức nền bắt buộc</strong> • Làm bài test chẩn đoán {pack.prerequisites.diagnosticPreTest.questions.length} câu trước khi học</>
            : <>Kiểm tra bạn đã đủ kiến thức nền để học tài liệu này chưa</>}
        </span>
        <ChevronRight className="w-4 h-4 shrink-0" />
      </button>

      {/* 3 Column Desktop Split View / Responsive Grid */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-12 overflow-hidden bg-slate-50">
        {/* Column 1: Dynamic Original Document Reader (4 cols) */}
        <div className="lg:col-span-4 bg-white border-r border-slate-200 p-5 overflow-y-auto space-y-4">
          <div className="space-y-3">
            <h3 className="font-extrabold text-base text-[#111827] border-b border-slate-100 pb-2">
              {doc?.title || 'Nội dung tài liệu trích xuất'}
            </h3>

            <div className="space-y-3 text-xs text-slate-700 leading-relaxed font-normal">
              {rawTextLines.map((line, idx) => {
                if (line.startsWith('"') || line.startsWith('“')) {
                  return (
                    <div key={idx} className="bg-amber-50/90 border-l-4 border-amber-400 p-3.5 rounded-r-xl text-xs text-amber-950 font-medium leading-relaxed shadow-2xs my-2">
                      {line}
                    </div>
                  );
                }
                return (
                  <p key={idx} className="whitespace-pre-line">
                    {line}
                  </p>
                );
              })}
            </div>
          </div>

          {/* Quick Action Pill Bar */}
          <div className="pt-4 flex flex-wrap gap-2 border-t border-slate-100">
            <button
              onClick={handleQuickCreateFlashcard}
              className="bg-teal-50 hover:bg-[#CCFBF1] text-[#0F766E] border border-teal-200 text-[11px] font-semibold px-3.5 py-1.5 rounded-full flex items-center gap-1.5 transition-all shadow-2xs"
            >
              <span>Tạo Thẻ ghi nhớ</span>
            </button>
            <button
              onClick={() => handleSendChat(`Giải thích thuật ngữ và công thức chính trong ${doc?.title}`)}
              className="bg-teal-50 hover:bg-[#CCFBF1] text-[#0F766E] border border-teal-200 text-[11px] font-semibold px-3.5 py-1.5 rounded-full flex items-center gap-1.5 transition-all shadow-2xs"
            >
              <span>Giải thích thuật ngữ</span>
            </button>
            <button
              onClick={handleQuickCreateMindmap}
              className="bg-teal-50 hover:bg-[#CCFBF1] text-[#0F766E] border border-teal-200 text-[11px] font-semibold px-3.5 py-1.5 rounded-full flex items-center gap-1.5 transition-all shadow-2xs"
            >
              <span>Vẽ sơ đồ nhánh</span>
            </button>
          </div>
        </div>

        {/* Column 2: AI Structured Summary Notes (4 cols) */}
        <div className="lg:col-span-4 bg-white border-r border-slate-200 p-5 overflow-y-auto space-y-5">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 className="font-bold text-xs uppercase tracking-wider text-[#0F766E] flex items-center gap-1.5">
              <Sparkles className="w-4 h-4" />
              <span>Ghi chú Tóm tắt AI</span>
            </h3>
          </div>

          {/* Notes Content */}
          <div className="space-y-4">
            <h4 className="font-bold text-base text-[#111827]">
              {pack?.notes?.summaryTitle || doc?.title || 'Tóm tắt nội dung bài học'}
            </h4>

            {pack?.notes?.sections?.map((sec, idx) => (
              <div key={idx} className="space-y-2">
                <h5 className="text-xs font-bold text-slate-500 uppercase tracking-wide">
                  {sec.heading}
                </h5>
                <ul className="space-y-2 text-xs text-[#111827]">
                  {sec.items?.map((item, i) => (
                    <li key={i} className="flex gap-2 items-start">
                      <span className="w-1.5 h-1.5 rounded-full bg-[#0F766E] mt-1.5 shrink-0" />
                      <div>
                        <strong className="font-semibold text-[#111827]">{item.label}: </strong>
                        <span className="text-slate-600">{item.text}</span>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          {/* Action Buttons */}
          <div className="pt-4 grid grid-cols-2 gap-2 border-t border-slate-100">
            <button
              onClick={handleQuickCreateMindmap}
              className="bg-[#CCFBF1] hover:bg-teal-200 text-[#0F766E] text-xs font-bold py-2 px-3 rounded-lg transition-all text-center"
            >
              Tạo sơ đồ tư duy từ ý này
            </button>
            <button
              onClick={handleQuickCreateQuiz}
              className="border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-bold py-2 px-3 rounded-lg transition-all text-center"
            >
              Tạo câu hỏi ôn luyện
            </button>
          </div>
        </div>

        {/* Column 3: AI Assistant Chat (4 cols) */}
        <div className="lg:col-span-4 bg-white flex flex-col justify-between p-5 h-full overflow-hidden">
          <div className="space-y-4 flex-1 overflow-y-auto pr-1">
            <div className="border-b border-slate-100 pb-3 space-y-2">
              <div className="grid grid-cols-2 gap-1 bg-slate-100 p-1 rounded-lg" role="tablist" aria-label="Chế độ trò chuyện">
                {([['qa', 'Hỏi đáp'], ['socratic', 'Gia sư Socrates']] as const).map(([mode, label]) => (
                  <button
                    key={mode}
                    role="tab"
                    aria-selected={chatMode === mode}
                    onClick={() => chatMode !== mode && switchChatMode(mode)}
                    className={`text-xs font-bold py-1.5 rounded-md transition-colors ${
                      chatMode === mode ? 'bg-white text-[#0F766E] shadow-xs' : 'text-slate-500 hover:text-slate-800'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
              {chatMode === 'socratic' && (
                <p className="text-[11px] text-slate-500 leading-relaxed">
                  Gia sư không đưa đáp án, chỉ đặt câu hỏi gợi mở để bạn tự suy luận.
                  {gapRoots.length > 0 && <> Đang tập trung vào: <strong className="text-rose-700">{gapRoots.map(r => r.node.label).join(', ')}</strong>.</>}
                </p>
              )}
            </div>

            {/* Messages */}
            <div className="space-y-3">
              {chatMessages.map((msg, index) => (
                <div
                  key={index}
                  className={`flex gap-2.5 ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  {msg.sender === 'ai' && (
                    <div className="w-7 h-7 rounded-full bg-[#0F766E] text-white flex items-center justify-center text-[10px] font-bold shrink-0 mt-0.5">
                      AI
                    </div>
                  )}
                  <div
                    className={`max-w-[85%] text-xs p-3 rounded-2xl leading-relaxed ${
                      msg.sender === 'user'
                        ? 'bg-[#0F766E] text-white rounded-br-none'
                        : 'bg-slate-100 text-[#111827]'
                    }`}
                  >
                    {msg.text}
                  </div>
                </div>
              ))}

              {isSending && (
                <div className="flex gap-2 text-xs text-slate-400 items-center">
                  <span>Đang suy nghĩ...</span>
                </div>
              )}
            </div>

            {/* Quick Suggestion Pill (asks for a direct summary, so Q&A mode only) */}
            <div className={`pt-2 ${chatMode === 'socratic' ? 'hidden' : ''}`}>
              <button
                onClick={() => handleSendChat(`Hãy tóm tắt ngắn gọn các công thức và khái niệm trọng tâm trong ${doc?.title}`)}
                className="w-full text-left bg-teal-50 hover:bg-teal-100/70 border border-teal-200/60 p-2.5 rounded-xl text-[11px] text-[#0F766E] font-medium transition-colors"
              >
                "Hãy tóm tắt ngắn gọn các công thức và kiến thức trọng tâm..."
              </button>
            </div>
          </div>

          {/* Chat Input Box */}
          <div className="pt-3 border-t border-slate-100">
            <form
              onSubmit={(e) => {
                e.preventDefault();
                handleSendChat();
              }}
              className="relative flex items-center"
            >
              <input
                type="text"
                value={inputQuery}
                onChange={(e) => setInputQuery(e.target.value)}
                placeholder="Nhập câu hỏi học tập tại đây..."
                className="w-full bg-slate-50 border border-slate-200 rounded-full pl-4 pr-10 py-2.5 text-xs text-[#111827] focus:outline-none focus:border-[#0F766E] focus:ring-1 focus:ring-[#0F766E]"
              />
              <button
                type="submit"
                disabled={!inputQuery.trim() || isSending}
                className="absolute right-1.5 w-7 h-7 bg-[#0F766E] hover:bg-[#0D5C53] text-white rounded-full flex items-center justify-center transition-transform hover:scale-105 disabled:opacity-40"
              >
                <Send className="w-3.5 h-3.5" />
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

