import React, { useState, useEffect } from 'react';
import { HelpCircle, Clock, Check, Sparkles, Award, Plus, X, RotateCcw } from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { QuizQuestion } from '../../types';

export function QuizView() {
  const { activeDocData, submitQuiz, addQuizQuestion, regenerateQuizAI, setActiveTab, showToast } = useStudy();

  const [isGenerating, setIsGenerating] = useState<boolean>(false);

  const handleGenerateAIQuiz = async () => {
    setIsGenerating(true);
    await regenerateQuizAI();
    setIsGenerating(false);
  };
  const quizData = activeDocData?.studyPack?.quiz || {
    title: "Luyện tập trắc nghiệm AI",
    subject: "Sinh học Tế bào - Ty thể và Chu trình chuyển hóa",
    timeLimitMinutes: 15,
    questions: [
      {
        id: "q-1",
        questionNumber: 1,
        questionText: "Đặc điểm cấu tạo nào sau đây của màng trong ty thể giúp tăng diện tích bề mặt chứa các phức hợp enzyme hô hấp?",
        options: [
          "A. Màng trơn nhẵn có các lỗ porin kích thước lớn",
          "B. Gấp nếp sâu tạo thành các mào (cristae)",
          "C. Chứa nhiều cholesterol làm cứng màng",
          "D. Bao bọc bởi lớp màng phospholipid kép tự do"
        ],
        correctIndex: 1,
        explanation: "Các mào (cristae) được tạo ra do màng trong gấp nếp sâu vào trong chất nền, làm tăng diện tích bề mặt tối đa cho các phản ứng chuỗi truyền electron."
      },
      {
        id: "q-2",
        questionNumber: 2,
        questionText: "Loại ADN nào được tìm thấy bên trong chất nền (matrix) của ty thể?",
        options: [
          "A. ADN dạng sợi thẳng liên kết với histon",
          "B. ADN dạng vòng kép tương tự như ở vi khuẩn",
          "C. ARN thông tin dLink",
          "D. Không có ADN, chỉ có Ribosome 80S"
        ],
        correctIndex: 1,
        explanation: "Ty thể chứa ADN vòng kép trần (không liên kết với histon) giống hệt ADN vi khuẩn, chứng minh nguồn gốc nội cộng sinh."
      },
      {
        id: "q-3",
        questionNumber: 3,
        questionText: "Thành phần lipid đặc trưng nào sau đây có trong màng trong ty thể giải thích khả năng chống thấm ion cực tốt để phục vụ chuỗi truyền electron?",
        options: [
          "A. Cholesterol nồng độ cao",
          "B. Cardiolipin đặc hữu",
          "C. Phosphatidylcholine liên kết peptid",
          "D. Glycoprotein màng ngoài"
        ],
        correctIndex: 1,
        explanation: "Đáp án B chính xác! Cardiolipin là một phospholipid kép có cấu trúc đặc hữu gồm 4 chuỗi axit béo, khiến cho màng trong cực kỳ dày đặc và không cho ion tự do đi qua, duy trì gradient proton H+ cần thiết cho phức hợp ATP Synthase hoạt động."
      }
    ]
  };

  const questions: QuizQuestion[] = quizData.questions;
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState<number>(0);
  const [userAnswers, setUserAnswers] = useState<Record<string, number>>({});
  const [secondsLeft, setSecondsLeft] = useState<number>(15 * 60);
  const [isSubmitted, setIsSubmitted] = useState<boolean>(false);
  const [scoreResult, setScoreResult] = useState<any>(null);

  // Add Question Modal State
  const [isAddModalOpen, setIsAddModalOpen] = useState<boolean>(false);
  const [qText, setQText] = useState<string>('');
  const [optA, setOptA] = useState<string>('');
  const [optB, setOptB] = useState<string>('');
  const [optC, setOptC] = useState<string>('');
  const [optD, setOptD] = useState<string>('');
  const [correctIdx, setCorrectIdx] = useState<number>(0);
  const [qExplanation, setQExplanation] = useState<string>('');

  useEffect(() => {
    const timer = setInterval(() => {
      setSecondsLeft((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  const currentQ = questions[currentQuestionIndex] || questions[0];
  const selectedOptionIndex = userAnswers[currentQ?.id];

  const handleSelectOption = (index: number) => {
    if (isSubmitted) return;
    setUserAnswers((prev) => ({
      ...prev,
      [currentQ.id]: index
    }));
  };

  const handleSubmitExam = async () => {
    const res = await submitQuiz(activeDocData?.document?.id || 'doc-1', userAnswers);
    if (res) {
      setScoreResult(res);
      setIsSubmitted(true);
      showToast(`Nộp bài thành công! Điểm của bạn: ${res.score}/100`);
    }
  };

  const handleRetake = () => {
    setUserAnswers({});
    setIsSubmitted(false);
    setScoreResult(null);
    setSecondsLeft(15 * 60);
    setCurrentQuestionIndex(0);
    showToast("Đã reset bài làm trắc nghiệm thành công!");
  };

  const handleSaveQuestion = async () => {
    if (!qText.trim() || !optA.trim() || !optB.trim()) return;
    await addQuizQuestion({
      questionText: qText,
      options: [`A. ${optA}`, `B. ${optB}`, `C. ${optC || 'Khác'}`, `D. ${optD || 'Khác'}`],
      correctIndex: correctIdx,
      explanation: qExplanation || "Đáp án đã được kiểm chứng bởi AI."
    });
    setQText('');
    setOptA('');
    setOptB('');
    setOptC('');
    setOptD('');
    setQExplanation('');
    setIsAddModalOpen(false);
  };

  return (
    <div className="p-4 md:p-8 space-y-6 max-w-7xl mx-auto pb-24 md:pb-8">
      {/* Title Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1">
          <h2 className="text-xl md:text-2xl font-bold text-[#111827] flex items-center gap-2">
            <HelpCircle className="w-5 h-5 text-[#0F766E]" />
            <span>Luyện tập trắc nghiệm</span>
          </h2>
          <p className="text-xs md:text-sm text-slate-500">
            Môn học: <strong className="text-[#111827]">{quizData.subject}</strong>
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap sm:flex-nowrap">
          <button
            onClick={handleGenerateAIQuiz}
            disabled={isGenerating}
            className="bg-[#0F766E] hover:bg-[#0D5C53] text-white text-xs font-bold px-4 py-2.5 rounded-xl shadow-sm transition-all flex items-center justify-center disabled:opacity-50"
          >
            <span>{isGenerating ? "Đang tạo 10-12 câu..." : "Tạo 10-12 câu hỏi"}</span>
          </button>
          <button
            onClick={handleRetake}
            className="bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-bold px-3.5 py-2.5 rounded-xl flex items-center justify-center shadow-2xs"
          >
            <span>Làm lại bài</span>
          </button>
          <button
            onClick={() => setIsAddModalOpen(true)}
            className="bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-bold px-3.5 py-2.5 rounded-xl flex items-center justify-center shadow-2xs"
          >
            <span>Thêm câu hỏi</span>
          </button>
        </div>
      </div>

      {/* Main Grid Layout: Question (8 cols) & Navigator (4 cols) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Question Box (8 cols) */}
        <div className="lg:col-span-8 bg-white border border-slate-200 rounded-2xl p-6 space-y-6 flex flex-col justify-between min-h-[500px]">
          {/* Header Bar */}
          <div className="flex items-center justify-between border-b border-slate-100 pb-4">
            <span className="text-xs font-bold text-slate-600">
              {quizData?.title || activeDocData?.document?.title || "Phần 1: Kiểm tra trắc nghiệm AI"}
            </span>
            <div className="flex items-center gap-1.5 text-xs font-bold text-rose-600 bg-rose-50 px-3 py-1 rounded-full border border-rose-100">
              <Clock className="w-3.5 h-3.5" />
              <span>Thời gian còn lại: {formatTime(secondsLeft)}</span>
            </div>
          </div>

          {/* Question Text */}
          {currentQ && (
            <div className="space-y-5">
              <h3 className="font-bold text-base md:text-lg text-[#111827] leading-snug">
                Câu hỏi {currentQuestionIndex + 1}: {currentQ.questionText}
              </h3>

              {/* Options List */}
              <div className="space-y-3">
                {currentQ.options.map((optText, idx) => {
                  const isSelected = selectedOptionIndex === idx;
                  const isCorrect = currentQ.correctIndex === idx;

                  let cardStyle = "bg-white border-slate-200 hover:border-slate-300 text-[#111827]";
                  let badge = null;

                  if (isSubmitted) {
                    if (isCorrect) {
                      cardStyle = "bg-emerald-50 border-emerald-500 text-emerald-900 font-semibold shadow-2xs";
                      badge = (
                        <div className="w-5 h-5 rounded-full bg-emerald-600 text-white flex items-center justify-center shrink-0">
                          <Check className="w-3.5 h-3.5" />
                        </div>
                      );
                    } else if (isSelected && !isCorrect) {
                      cardStyle = "bg-rose-50 border-rose-400 text-rose-900 font-semibold shadow-2xs";
                      badge = (
                        <div className="w-5 h-5 rounded-full bg-rose-600 text-white flex items-center justify-center shrink-0">
                          <X className="w-3.5 h-3.5" />
                        </div>
                      );
                    }
                  } else {
                    if (isSelected) {
                      cardStyle = "bg-[#CCFBF1]/40 border-[#0F766E] text-[#0F766E] shadow-2xs font-semibold";
                      badge = (
                        <div className="w-5 h-5 rounded-full bg-[#0F766E] text-white flex items-center justify-center shrink-0">
                          <Check className="w-3.5 h-3.5" />
                        </div>
                      );
                    }
                  }

                  return (
                    <div
                      key={idx}
                      onClick={() => handleSelectOption(idx)}
                      className={`p-4 rounded-xl border-2 transition-all cursor-pointer flex items-center justify-between font-medium text-xs md:text-sm ${cardStyle}`}
                    >
                      <span>{optText}</span>
                      {badge}
                    </div>
                  );
                })}
              </div>

              {/* AI Feedback Box - ONLY SHOWN AFTER SUBMITTING EXAM */}
              {isSubmitted && (
                <div className="p-4 rounded-xl bg-teal-50 border border-teal-200 space-y-2 text-xs text-teal-900 animate-fadeIn">
                  <div className="flex items-center gap-1.5 font-bold text-[#0F766E]">
                    <Sparkles className="w-4 h-4 text-[#0F766E]" />
                    <span>Giải thích chi tiết từ AI:</span>
                  </div>
                  <p className="leading-relaxed font-medium">
                    {currentQ.explanation}
                  </p>
                </div>
              )}
            </div>
          )}

          {/* Bottom Prev/Next Controls */}
          <div className="flex justify-between items-center pt-4 border-t border-slate-100">
            <button
              onClick={() => setCurrentQuestionIndex((prev) => Math.max(0, prev - 1))}
              disabled={currentQuestionIndex === 0}
              className="px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100 rounded-lg disabled:opacity-30"
            >
              ← Câu trước
            </button>
            <button
              onClick={() => setCurrentQuestionIndex((prev) => Math.min(questions.length - 1, prev + 1))}
              disabled={currentQuestionIndex === questions.length - 1}
              className="px-4 py-2 text-xs font-bold text-[#0F766E] hover:bg-teal-50 rounded-lg disabled:opacity-30"
            >
              Câu sau →
            </button>
          </div>
        </div>

        {/* Right Navigator Panel (4 cols) */}
        <div className="lg:col-span-4 bg-white border border-slate-200 rounded-2xl p-6 space-y-6 flex flex-col justify-between">
          <div className="space-y-5">
            <h3 className="font-bold text-base text-[#111827] border-b border-slate-100 pb-3">
              Tiến trình kiểm tra
            </h3>

            {/* Grid of Question Numbers */}
            <div className="grid grid-cols-4 gap-2.5">
              {questions.map((q, idx) => {
                const isAnswered = userAnswers[q.id] !== undefined;
                const isCurrent = currentQuestionIndex === idx;

                return (
                  <button
                    key={q.id || idx}
                    onClick={() => setCurrentQuestionIndex(idx)}
                    className={`h-10 rounded-lg text-xs font-bold transition-all flex items-center justify-center ${
                      isCurrent
                        ? 'bg-[#0F766E] text-white ring-2 ring-[#0F766E]/30 shadow-xs'
                        : isAnswered
                        ? 'bg-[#CCFBF1] text-[#0F766E] border border-[#0F766E]/40'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    {idx + 1}
                  </button>
                );
              })}
            </div>

            {/* Progress Meta Info */}
            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-500 font-medium">Đã trả lời:</span>
                <span className="font-bold text-[#111827]">
                  {Object.keys(userAnswers).length} / {questions.length} câu
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500 font-medium">Tỷ lệ hoàn thành:</span>
                <span className="font-bold text-[#10B981]">
                  {Math.round((Object.keys(userAnswers).length / Math.max(1, questions.length)) * 100)}%
                </span>
              </div>
            </div>
          </div>

          {/* Submit Exam Button */}
          <div>
            <button
              onClick={handleSubmitExam}
              className="w-full bg-[#0F766E] hover:bg-[#0D5C53] text-white font-bold text-sm py-3 rounded-lg shadow-md transition-all hover:scale-101 flex items-center justify-center gap-2"
            >
              <span>Nộp bài và xem kết quả</span>
            </button>
          </div>
        </div>
      </div>

      {/* Score Summary Modal */}
      {isSubmitted && scoreResult && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center z-50 p-4 animate-fadeIn">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 text-center space-y-5 shadow-2xl border border-slate-100">
            <div className="w-16 h-16 bg-emerald-100 text-[#10B981] rounded-full flex items-center justify-center mx-auto shadow-inner">
              <Award className="w-8 h-8" />
            </div>

            <div className="space-y-2">
              <h3 className="text-2xl font-extrabold text-[#111827]">
                Điểm số: {scoreResult.score}/100
              </h3>
              <p className="text-xs text-slate-600 font-medium">
                Bạn trả lời đúng <strong className="text-[#0F766E]">{scoreResult.correctCount}/{scoreResult.totalQuestions}</strong> câu hỏi.
              </p>
              <p className="text-xs text-slate-500 bg-slate-50 p-3 rounded-xl border border-slate-200">
                {scoreResult.feedback}
              </p>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => setIsSubmitted(false)}
                className="flex-1 bg-[#0F766E] hover:bg-[#0D5C53] text-white font-bold text-xs py-2.5 rounded-lg shadow-sm"
              >
                Xem chi tiết câu trả lời
              </button>
              <button
                onClick={handleRetake}
                className="flex-1 border border-slate-200 hover:bg-slate-50 text-slate-700 font-bold text-xs py-2.5 rounded-lg"
              >
                Làm lại bài kiểm tra
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Custom Quiz Question Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center z-50 p-4 animate-fadeIn">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 space-y-5 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-base text-[#111827]">Thêm câu hỏi trắc nghiệm mới</h3>
              <button onClick={() => setIsAddModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4">
              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-700">Nội dung câu hỏi:</label>
                <textarea
                  rows={2}
                  value={qText}
                  onChange={(e) => setQText(e.target.value)}
                  placeholder="VD: Ty thể tổng hợp ATP bằng enzyme nào?"
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs focus:outline-none focus:border-[#0F766E]"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <label className="block text-[11px] font-bold text-slate-600">Phương án A:</label>
                  <input
                    type="text"
                    value={optA}
                    onChange={(e) => setOptA(e.target.value)}
                    placeholder="VD: ATP Synthase"
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="block text-[11px] font-bold text-slate-600">Phương án B:</label>
                  <input
                    type="text"
                    value={optB}
                    onChange={(e) => setOptB(e.target.value)}
                    placeholder="VD: Polymerase"
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="block text-[11px] font-bold text-slate-600">Phương án C:</label>
                  <input
                    type="text"
                    value={optC}
                    onChange={(e) => setOptC(e.target.value)}
                    placeholder="VD: Ribozyme"
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="block text-[11px] font-bold text-slate-600">Phương án D:</label>
                  <input
                    type="text"
                    value={optD}
                    onChange={(e) => setOptD(e.target.value)}
                    placeholder="VD: Ligase"
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-1.5 text-xs"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-700">Đáp án đúng:</label>
                <select
                  value={correctIdx}
                  onChange={(e) => setCorrectIdx(Number(e.target.value))}
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-xs"
                >
                  <option value={0}>A (Phương án 1)</option>
                  <option value={1}>B (Phương án 2)</option>
                  <option value={2}>C (Phương án 3)</option>
                  <option value={3}>D (Phương án 4)</option>
                </select>
              </div>

              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-700">Giải thích chi tiết từ AI:</label>
                <input
                  type="text"
                  value={qExplanation}
                  onChange={(e) => setQExplanation(e.target.value)}
                  placeholder="VD: ATP Synthase là enzyme màng trong thực hiện tổng hợp ATP nhờ gradient proton."
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2 text-xs"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2 border-t border-slate-100">
              <button
                onClick={handleSaveQuestion}
                className="flex-1 bg-[#0F766E] hover:bg-[#0D5C53] text-white font-bold text-xs py-2.5 rounded-lg shadow-sm flex items-center justify-center gap-1.5"
              >
                <Check className="w-4 h-4" />
                <span>Thêm câu hỏi</span>
              </button>
              <button
                onClick={() => setIsAddModalOpen(false)}
                className="flex-1 border border-slate-200 hover:bg-slate-50 text-slate-700 font-bold text-xs py-2.5 rounded-lg"
              >
                Hủy
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
