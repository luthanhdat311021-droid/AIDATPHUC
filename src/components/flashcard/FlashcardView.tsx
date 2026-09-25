import React, { useState } from 'react';
import { Layers, RotateCw, Play, Plus, Trash2, X, Check, Sparkles } from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { Flashcard } from '../../types';

export function FlashcardView() {
  const { activeDocData, reviewFlashcard, addFlashcard, deleteFlashcard, regenerateFlashcardsAI } = useStudy();
  const [isGenerating, setIsGenerating] = useState<boolean>(false);

  const handleGenerateAIFlashcards = async () => {
    setIsGenerating(true);
    await regenerateFlashcardsAI();
    setIsGenerating(false);
  };
  const flashcards: Flashcard[] = activeDocData?.studyPack?.flashcards || [
    {
      id: "fc-1",
      front: "Thuyết nội cộng sinh (Endosymbiotic Theory) giải thích điều gì về nguồn gốc ty thể?",
      back: "Giải thích ty thể vốn là vi khuẩn hiếu khí sống cộng sinh bên trong tế bào chủ nhân thực cổ đại, sau này trở thành bào quan tổng hợp ATP.",
      difficulty: "medium"
    },
    {
      id: "fc-2",
      front: "Vai trò chính của Cardiolipin trong màng trong ty thể là gì?",
      back: "Cardiolipin là phospholipid kép đặc hữu làm màng trong không thấm ion H+, giúp duy trì gradient proton cho enzyme ATP Synthase.",
      difficulty: "hard"
    },
    {
      id: "fc-3",
      front: "Chu trình Krebs (TCA cycle) diễn ra tại vị trí nào của ty thể?",
      back: "Diễn ra tại Chất nền (Matrix) của ty thể nhờ hệ enzyme hòa tan đặc hiệu.",
      difficulty: "easy"
    }
  ];

  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [isFlipped, setIsFlipped] = useState<boolean>(false);

  // Add Card Modal State
  const [isAddModalOpen, setIsAddModalOpen] = useState<boolean>(false);
  const [cardFront, setCardFront] = useState<string>('');
  const [cardBack, setCardBack] = useState<string>('');
  const [cardDifficulty, setCardDifficulty] = useState<'easy' | 'medium' | 'hard'>('medium');

  const currentCard = flashcards[currentIndex] || flashcards[0];

  const handleRate = async (rating: string) => {
    if (currentCard) {
      await reviewFlashcard(currentCard.id, rating);
    }
    setIsFlipped(false);
    setCurrentIndex((prev) => (prev + 1) % Math.max(1, flashcards.length));
  };

  const handleSaveCard = async () => {
    if (!cardFront.trim() || !cardBack.trim()) return;
    await addFlashcard({
      front: cardFront,
      back: cardBack,
      difficulty: cardDifficulty
    });
    setCardFront('');
    setCardBack('');
    setIsAddModalOpen(false);
  };

  const handleDeleteCurrentCard = async () => {
    if (!currentCard) return;
    if (window.confirm("Bạn có chắc muốn xóa thẻ ghi nhớ này?")) {
      await deleteFlashcard(currentCard.id);
      setCurrentIndex(0);
    }
  };

  return (
    <div className="p-4 md:p-8 space-y-6 max-w-7xl mx-auto pb-24 md:pb-8">
      {/* Title Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1">
          <h2 className="text-xl md:text-2xl font-bold text-[#111827] flex items-center gap-2">
            <Layers className="w-5 h-5 text-[#0F766E]" />
            <span>Học tập trung cùng Flashcard</span>
          </h2>
          <p className="text-xs md:text-sm text-slate-500">
            Ôn tập thẻ từ vựng và câu hỏi cấu trúc siêu vi tế bào hiệu quả
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap sm:flex-nowrap">
          <button
            onClick={handleGenerateAIFlashcards}
            disabled={isGenerating}
            className="bg-[#0F766E] hover:bg-[#0D5C53] text-white text-xs font-bold px-4 py-2.5 rounded-xl shadow-sm transition-all flex items-center justify-center disabled:opacity-50"
          >
            <span>{isGenerating ? "Đang tạo 10-12 thẻ..." : "Tạo 10-12 Thẻ ghi nhớ"}</span>
          </button>
          <button
            onClick={() => setIsAddModalOpen(true)}
            className="bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-bold px-3.5 py-2.5 rounded-xl shadow-2xs transition-all flex items-center justify-center"
          >
            <span>Tạo thẻ mới</span>
          </button>
        </div>
      </div>

      {/* Main Grid: Card Viewer (8 cols) & Deck Sidebar (4 cols) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Flashcard Area (8 cols) */}
        <div className="lg:col-span-8 bg-white border border-slate-200 rounded-2xl p-6 space-y-6 flex flex-col justify-between min-h-[480px]">
          {/* Header Progress */}
          <div className="flex items-center justify-between border-b border-slate-100 pb-4">
            <span className="text-xs font-semibold text-slate-600">
              Phiên học: <strong className="text-[#111827]">{activeDocData?.document?.title || "Tài liệu học tập"}</strong>
            </span>
            <div className="flex items-center gap-2">
              <span className="text-xs font-extrabold text-[#0F766E] bg-teal-50 px-3 py-1 rounded-full border border-teal-100">
                Thẻ {currentIndex + 1} / {Math.max(1, flashcards.length)}
              </span>
              {currentCard && (
                <button
                  onClick={handleDeleteCurrentCard}
                  className="p-1.5 hover:bg-rose-50 text-slate-400 hover:text-rose-600 rounded-lg transition-colors"
                  title="Xóa thẻ này"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              )}
            </div>
          </div>

          {/* Interactive Flip Card Component */}
          <div className="flex-1 flex items-center justify-center py-6">
            <div
              onClick={() => setIsFlipped(!isFlipped)}
              className="w-full max-w-xl min-h-[260px] bg-slate-50 hover:bg-slate-100/80 border-2 border-slate-200/80 hover:border-[#0F766E]/40 rounded-2xl p-8 flex flex-col justify-between items-center text-center cursor-pointer transition-all duration-300 shadow-sm hover:shadow-md transform hover:-translate-y-1"
            >
              {!isFlipped ? (
                /* FRONT SIDE */
                <div className="space-y-4 my-auto">
                  <span className="text-[11px] font-bold tracking-wider uppercase text-[#0F766E] bg-[#CCFBF1] px-3 py-1 rounded-full">
                    CÂU HỎI (MẶT TRƯỚC)
                  </span>
                  <h3 className="text-lg md:text-xl font-bold text-[#111827] leading-relaxed">
                    {currentCard ? currentCard.front : 'Chưa có thẻ nào'}
                  </h3>
                </div>
              ) : (
                /* BACK SIDE */
                <div className="space-y-4 my-auto">
                  <span className="text-[11px] font-bold tracking-wider uppercase text-emerald-700 bg-emerald-100 px-3 py-1 rounded-full">
                    ĐÁP ÁN (MẶT SAU)
                  </span>
                  <p className="text-sm md:text-base font-semibold text-[#111827] leading-relaxed">
                    {currentCard ? currentCard.back : 'Nội dung đáp án'}
                  </p>
                </div>
              )}

              <div className="pt-4 flex items-center gap-1.5 text-xs font-medium text-slate-400">
                <RotateCw className="w-3.5 h-3.5 text-[#0F766E]" />
                <span>{isFlipped ? 'Nhấp để lật về mặt trước' : 'Nhấp vào thẻ để lật xem đáp án'}</span>
              </div>
            </div>
          </div>

          {/* 3 Rating Spaced Repetition Buttons */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-4 border-t border-slate-100">
            <button
              onClick={() => handleRate('hard')}
              className="bg-rose-50 hover:bg-rose-100 border border-rose-200 text-rose-700 font-bold text-xs py-3 px-4 rounded-xl transition-all flex flex-col items-center gap-0.5"
            >
              <span>🔴 Chưa thuộc (Khó)</span>
              <span className="text-[10px] font-medium text-rose-500">Ôn lại sau 1 phút</span>
            </button>

            <button
              onClick={() => handleRate('medium')}
              className="bg-amber-50 hover:bg-amber-100 border border-amber-200 text-amber-800 font-bold text-xs py-3 px-4 rounded-xl transition-all flex flex-col items-center gap-0.5"
            >
              <span>🟡 Tương đối (Trung bình)</span>
              <span className="text-[10px] font-medium text-amber-600">Ôn lại sau 10 phút</span>
            </button>

            <button
              onClick={() => handleRate('easy')}
              className="bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-800 font-bold text-xs py-3 px-4 rounded-xl transition-all flex flex-col items-center gap-0.5"
            >
              <span>🟢 Đã thuộc (Dễ)</span>
              <span className="text-[10px] font-medium text-emerald-600">Ôn lại sau 4 ngày</span>
            </button>
          </div>
        </div>

        {/* Right Decks List Sidebar (4 cols) */}
        <div className="lg:col-span-4 bg-white border border-slate-200 rounded-2xl p-6 space-y-4">
          <h3 className="font-bold text-base text-[#111827] border-b border-slate-100 pb-3">
            Danh sách các bộ thẻ của bạn
          </h3>

          <div className="space-y-3">
            <div className="p-4 rounded-xl bg-teal-50 border border-teal-200 flex items-center justify-between cursor-pointer">
              <div className="space-y-1">
                <h4 className="font-bold text-xs md:text-sm text-[#0F766E]">
                  Thuyết nội cộng sinh Ty thể
                </h4>
                <p className="text-[11px] text-teal-700 font-medium">
                  Tiến độ: <span className="font-bold">{flashcards.length} thẻ</span>
                </p>
              </div>
              <div className="w-8 h-8 rounded-full bg-[#0F766E] text-white flex items-center justify-center shrink-0">
                <Play className="w-4 h-4 fill-current ml-0.5" />
              </div>
            </div>

            <div className="p-4 rounded-xl border border-slate-200 hover:bg-slate-50 transition-colors flex items-center justify-between cursor-pointer">
              <div className="space-y-1">
                <h4 className="font-semibold text-xs md:text-sm text-[#111827]">
                  Cấu trúc siêu vi và enzyme hô hấp
                </h4>
                <p className="text-[11px] text-slate-400">
                  Tiến độ: 12 / 52 thẻ
                </p>
              </div>
              <div className="w-8 h-8 rounded-full bg-slate-100 text-slate-500 flex items-center justify-center shrink-0">
                <Play className="w-4 h-4 fill-current ml-0.5" />
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Add Custom Flashcard Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center z-50 p-4 animate-fadeIn">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 space-y-5 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-base text-[#111827]">Tạo Thẻ ghi nhớ mới</h3>
              <button onClick={() => setIsAddModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4">
              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-700">Mặt trước (Câu hỏi/Thuật ngữ):</label>
                <textarea
                  rows={2}
                  value={cardFront}
                  onChange={(e) => setCardFront(e.target.value)}
                  placeholder="VD: Ty thể tự nhân đôi theo cơ chế nào?"
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs focus:outline-none focus:border-[#0F766E]"
                />
              </div>

              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-700">Mặt sau (Đáp án/Giải thích):</label>
                <textarea
                  rows={3}
                  value={cardBack}
                  onChange={(e) => setCardBack(e.target.value)}
                  placeholder="VD: Phân đôi độc lập tương tự như cơ chế sinh sản ở vi khuẩn."
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs focus:outline-none focus:border-[#0F766E]"
                />
              </div>

              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-700">Đánh giá độ khó ban đầu:</label>
                <select
                  value={cardDifficulty}
                  onChange={(e) => setCardDifficulty(e.target.value as any)}
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2 text-xs text-[#111827]"
                >
                  <option value="easy">🟢 Dễ (Easy)</option>
                  <option value="medium">🟡 Trung bình (Medium)</option>
                  <option value="hard">🔴 Khó (Hard)</option>
                </select>
              </div>
            </div>

            <div className="flex gap-2 pt-2 border-t border-slate-100">
              <button
                onClick={handleSaveCard}
                className="flex-1 bg-[#0F766E] hover:bg-[#0D5C53] text-white font-bold text-xs py-2.5 rounded-lg shadow-sm flex items-center justify-center gap-1.5"
              >
                <Check className="w-4 h-4" />
                <span>Tạo thẻ ngay</span>
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
