# StudyMind — Android native (100% Java)

Bản Android viết bằng Java + Android View (Material 3), tương đồng bản WebView trong `../android`.
Dùng chung tài khoản và dữ liệu Supabase với bản web; API chạy trên Supabase Edge Functions (không cần Vercel).

## Mở & chạy
1. Android Studio → **Open** → chọn thư mục `android-native/`.
2. Chạy cấu hình `app` trên emulator/điện thoại (minSdk 26).

Dòng lệnh:
```
gradlew.bat assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. Gradle 9.1 + AGP 8.13 chạy được với JBR 25 đi kèm Android Studio.

`applicationId` là `com.studymind.nativeapp`, nên cài song song được với bản WebView (`com.studymind.app`).

## Cấu trúc
| Thư mục | Nội dung |
|---|---|
| `data/` | `AuthRepository` (Supabase Auth REST), `ApiClient` (backend, tự refresh token, bắt 402 → mở nâng cấp), `AppState` (tương đương `StudyContext`), `KnowledgeGaps` (port `knowledgeGaps.ts`), `FileIngest` (trích chữ DOCX/PPTX/TXT, nén ảnh ≤ 4MB) |
| `ui/screens/` | Đăng nhập, Tổng quan, Nhập tài liệu, Lịch sử, Không gian tài liệu (nguồn / ghi chú + đọc to / gia sư AI Hỏi đáp & Socrates), Kiến thức tiên quyết, Bản đồ lỗ hổng, Sơ đồ tư duy, Thẻ ghi nhớ, Trắc nghiệm, Hợp nhất kiến thức, Tài khoản Pro, Quản trị |
| `ui/dialogs/` | `UpgradeSheet` (QR SePay + chờ webhook), `ProfileDialog`, `PomodoroDialog` |

## Phân quyền
App chỉ *hiển thị* theo `role`/`plan` mà server trả về (`/api/v1/billing/me`); mọi quyền Pro/admin
được backend kiểm tra lại ở từng request, nên sửa APK cũng không mở khóa được gì.

| Vai trò | Trong app |
|---|---|
| Free | "Chuyên sâu" và "Hợp nhất kiến thức" có 🔒; hạn mức AI theo ngày; nút "Nâng cấp Pro" ở sidebar, Tổng quan, Tài khoản Pro |
| Pro | Mở khóa toàn bộ; trang Tài khoản Pro hiện hạn sử dụng và hạn mức |
| Admin | Có thêm mục **Quản trị** (doanh thu, đơn hàng, log webhook SePay, cấp/thu hồi Pro theo email) |

Server trả **402** (tính năng Pro / hết lượt AI) → app tự mở khung thanh toán.

## Server mà app gọi
| Phần | Ở đâu |
|---|---|
| Đăng nhập, dữ liệu | Supabase (Auth + Postgres, RLS) |
| Pro, đơn SePay, Quản trị | Supabase Edge Function `studymind-billing` (code: `supabase/functions/studymind-billing`) |
| Webhook SePay | Supabase Edge Function `sepay-webhook` → URL `https://gwynjmlqymojrdlpzukn.supabase.co/functions/v1/sepay-webhook` |
| AI và dữ liệu bài học (nhập tài liệu, quiz, chat…) | Supabase Edge Function `studymind-api`: cùng các route Express với backend web (`backend/routes/studyRoutes.js`) |
| Tệp tải lên (≤ 50MB), ảnh đại diện | Supabase Storage: bucket `study-uploads` (riêng tư) và `avatars` (công khai) |

App không còn gọi Vercel (chỉ mở trang chính sách và link chia sẻ của web).
Một bài học được tạo qua 3 bước ngắn: `lessons/start` → `lessons/:id/analyze` (lặp lại) → `lessons/:id/finish`,
để mỗi request nằm trong giới hạn 150 giây của Edge Function.
Build lại `studymind-api` sau khi sửa `backend/`: `node supabase/functions/studymind-api/build.mjs` → deploy `index.ts`.
Secret cần có: `GEMINI_API_KEY`, `GROQ_API_KEY`.

Cả 3 Edge Function đều tắt "Verify JWT" vì tự xác thực: `studymind-api` và `studymind-billing` kiểm tra token đăng nhập Supabase,
`sepay-webhook` kiểm tra header `Authorization: Apikey <SEPAY_WEBHOOK_API_KEY>` (đặt trong Edge Functions → Secrets).

## Thanh toán SePay
`UpgradeSheet`: chọn gói → Edge Function tạo đơn (`POST studymind-billing/orders`) → hiện VietQR của SePay
(MBBank 0819883208, nội dung `SMxxxxxx`) → chuyển khoản → webhook SePay báo về backend → app hỏi lại
trạng thái đơn mỗi 3 giây và mở khóa Pro ngay khi đơn "paid". Đóng khung không hủy đơn.
Không phụ thuộc deploy Vercel; Edge Functions tự có `SUPABASE_SERVICE_ROLE_KEY`.

## Đăng nhập Google
Thêm `com.studymind.nativeapp://auth/callback` vào Supabase → Authentication → URL Configuration → Redirect URLs.
