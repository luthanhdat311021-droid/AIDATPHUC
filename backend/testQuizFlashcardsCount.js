import { aiRouter } from './services/ai/AIRouter.js';

async function testGenerationCounts() {
  console.log("=== Testing 10-12 Flashcards & Quiz Generation ===");
  const dummyKnowledge = {
    title: "Mô tả nghiệp vụ cho nhà hàng Pizza (Project 8)",
    topics: ["Đặt bánh Pizza", "Bánh tự làm & Bánh làm sẵn", "Thanh toán & Qui trình"],
    concepts: [
      { id: "c-1", name: "Màn hình cảm ứng tại bàn", description: "Mỗi bàn được trang bị màn hình cảm ứng để xem danh sách bánh.", importance: 5 },
      { id: "c-2", name: "Loại 1: Bánh tự làm", description: "Có thành phần cơ bản là sốt cà chua, khách chọn thành phần khác.", importance: 5 },
      { id: "c-3", name: "Loại 2: Bánh làm sẵn", description: "Các thành phần đã được chọn sẵn không thay đổi.", importance: 4 },
      { id: "c-4", name: "Vỏ bánh & Kích thước", description: "Khách chọn vỏ giòn/dày và kích thước nhỏ, trung, lớn.", importance: 4 },
      { id: "c-5", name: "Đồ uống đi kèm", description: "Khách có thể đặt thêm Cô-Ca, nước chanh đi kèm bánh.", importance: 3 },
      { id: "c-6", name: "Tổng tiền & Qui trình", description: "Hiển thị tổng tiền và qui trình chuẩn bị làm bánh.", importance: 4 },
      { id: "c-7", name: "Hình thức thanh toán", description: "Thanh toán bằng tiền mặt hoặc thẻ tín dụng.", importance: 5 }
    ]
  };

  const flashcards = await aiRouter.generateFlashcards(dummyKnowledge);
  console.log(`\n✅ Generated Flashcards Count: ${flashcards.length}`);
  console.log("Sample Flashcard 1:", flashcards[0]);

  const quiz = await aiRouter.generateQuiz(dummyKnowledge);
  console.log(`\n✅ Generated Quiz Questions Count: ${quiz.questions.length}`);
  console.log("Sample Question 1:", quiz.questions[0]);
}

testGenerationCounts();
