# StudyCoin: tài khoản aihoctap, blockchain staging

Android tiếp tục dùng `gwynjmlqymojrdlpzukn` cho Auth, bài học, Storage và billing.
Không đổi URL/khóa trong APK. Backend chính chuyển riêng `/api/v1/rewards/*` sang
`urnlxgyqmvcbkonysizy`. Khóa operator và ví gốc không rời staging.

## Ranh giới xác thực

- Backend chính xác thực JWT và đọc quyền trong database như trước, rồi ký thông điệp HMAC-SHA256.
- Staging chỉ nhận thông điệp ký đúng, đúng mục đích, trong 120 giây; nonce được lưu trong database để chặn phát lại.
- Hai chế độ được khóa theo project ref. URL đích cố định và không theo HTTP redirect.
- Staging ánh xạ UUID chính sang UUID riêng. Bản ghi Auth phụ không có email, mật khẩu hay identity đăng nhập và bị cấm đăng nhập vĩnh viễn. Ngày tạo và trạng thái xác minh email được lấy từ server chính để giữ điều kiện referral. Không sao chép bài học, mật khẩu hoặc access token.
- Quyền admin được xác minh ở chính và áp dụng cho bản ghi phụ để các RPC admin hiện có tiếp tục kiểm tra quyền.
- Chỉ backend ghi nhận ôn thẻ/quiz đủ điều kiện. Luồng này vẫn là best-effort như trước; lỗi kết nối không làm hỏng việc học nhưng sự kiện đó có thể chưa được tính thưởng.
- Burn chỉ tạo outbox sau khi worker xác minh receipt và xác nhận giao dịch. Worker HTTP và CLI đều gửi lại outbox khi cần.
- Server chính chỉ nhận contract Sepolia đã cấu hình. SQL cộng thêm số ngày vào Pro hiện tại, giữ nguyên role, chống trùng theo job và theo transaction/log; payload thay đổi khi gửi lại bị từ chối.
- Pro có thể cập nhật sau lượt worker tiếp theo; không suy ra quyền Pro từ số dư token hay kết quả trên điện thoại.

## Triển khai (chưa tự bật bằng mã nguồn)

1. Sao lưu database/bundle hiện có. Kiểm tra staging đã có migrations 002–009. File bridge staging bổ sung hai bảng biometric từ 011 nếu chưa có. Nếu thiếu migrations nền, áp dụng migration tương ứng; không bỏ bước xác thực sinh trắc khi đổi token.
2. Chạy `supabase/bridge/primary.sql` **chỉ trên aihoctap** và `supabase/bridge/staging.sql` **chỉ trên staging**. Hai file không nằm trong migrations tự động vì dành cho hai database khác nhau.
3. Tạo một `REWARDS_BRIDGE_SECRET` ngẫu nhiên tối thiểu 32 byte, lưu cùng giá trị vào Edge Function secrets của hai project. Không dùng service-role key/operator key làm khóa này và không đưa nó vào APK, repo hoặc log.
4. Deploy cùng bundle `studymind-api/index.ts` mới lên hai project (build bằng `node supabase/functions/studymind-api/build.mjs`). Giữ cấu hình Verify JWT hiện tại của function: chức năng này tự xác thực; bridge dùng HMAC, các API người dùng vẫn dùng Supabase Auth.
5. Staging: giữ toàn bộ `REWARDS_*` hiện tại, thêm `REWARDS_BRIDGE_MODE=target`. Chính: thêm `REWARDS_BRIDGE_MODE=source`. Không cần thêm operator key, xpub hoặc `REWARDS_ENABLED` cho project chính.
6. Bảo đảm scheduler staging gọi `/api/internal/rewards/run` mỗi phút bằng worker secret hiện có, hoặc CLI worker chạy với môi trường staging và các biến bridge. Không dùng worker local-chain cho contract Sepolia.
7. Kiểm thử tài khoản chính: `/rewards/me`, ôn thẻ/quiz, ví/mission; kiểm tra chữ ký sinh trắc; burn thử với token test và xác nhận Pro trên chính chỉ tăng một lần sau xác nhận. Kiểm tra outbox không tồn đọng và endpoint không chữ ký trả 401/403. Không tạo token hoặc cấp Pro giả để vượt qua kiểm thử.

## Kiểm tra và rollback

`node --test blockchain/test/bridge.test.mjs` kiểm tra chữ ký, route allowlist, ánh xạ,
nonce, quyền SQL, outbox và cấp Pro chống trùng. Chạy thêm suite blockchain trước deploy.

Tắt `REWARDS_BRIDGE_MODE` trên chính sẽ quay về backend rewards cũ và trạng thái cũ.
Nếu có burn đang chờ/Pro chưa nhận, giữ worker và bridge target/source grant receiver hoạt động
cho đến khi outbox đã giao xong; không tắt receiver giữa chừng. Không xóa mapping, outbox,
receipts hoặc tạo lại xpub/contract. Khi cần dừng tiếp nhận mới nhưng vẫn giao Pro, chặn
route proxy ở lớp vận hành trước khi tắt source mode.

Giới hạn tin cậy: backend staging được phép chứng thực các burn của contract testnet để cấp
Pro trên chính. Chỉ bật sau khi chấp thuận mối liên kết này. Khóa HMAC bị lộ cho phép giả
mạo yêu cầu bridge; cần giữ nó trong secrets và thay đồng thời ở hai project khi xoay khóa.

## Kết quả triển khai ngày 08/11/2026

Đã áp dụng primary.sql và staging.sql, deploy studymind-api lên cả hai project, đặt shared HMAC secret và bật mode source/target. Verify JWT của function giữ nguyên false; API người dùng vẫn xác thực Supabase JWT trong backend, bridge yêu cầu HMAC.

Scheduler `studymind-rewards-sepolia` đã bật mỗi phút và lần chạy được kiểm tra có trạng thái `succeeded`. Khóa worker hiện có được lưu trong Supabase Vault với tên `studymind_rewards_worker`. Scheduler chỉ gọi worker khi có jobs/referrals hoặc Pro outbox cần xử lý. SQL tái lập ở `supabase/bridge/scheduler.sql`; theo hướng dẫn chính thức: https://supabase.com/docs/guides/functions/schedule-functions.

Kiểm tra live qua tài khoản thử riêng trên API chính: HTTP 200, enabled=true, chainId=11155111, network=Ethereum Sepolia, chainAvailable=true, tạo được ví và trả 6 nhiệm vụ. Yêu cầu cấp Pro không chữ ký bị trả 401. Worker trả success, failed=0; không có Pro outbox chờ. Tài khoản và dữ liệu thử đã được xóa. Không mint/burn thử trong lần triển khai này; kiểm thử giao dịch thực trên điện thoại do người dùng thực hiện.

Bản sao backend trước deploy nằm trong `android-native/.bridge-deploy/primary-backup` và `staging-backup` (được Git bỏ qua). Android không cần rebuild hay đổi Supabase URL.
