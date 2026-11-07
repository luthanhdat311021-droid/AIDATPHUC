# StudyCoin — phương án B

StudyCoin là token ERC-20 không chuyển nhượng, số nguyên (`decimals = 0`). Số dư trên smart contract quyết định khả năng sử dụng; Postgres chỉ giữ bằng chứng học, hàng đợi giao dịch và bản đối soát. Không có ví xu nội bộ có thể chi tiêu song song.

## Ví và quyền quản lý

Mỗi tài khoản Supabase có một địa chỉ ví HD cố định. Bản thử nghiệm dùng ví do StudyMind quản lý; người dùng khôi phục quyền truy cập bằng tài khoản StudyMind, không có chức năng xuất khóa hoặc dùng ví ngoài. Server chỉ cần **xpub ở đường dẫn `m/44'/60'/0'/0`** để tạo địa chỉ theo `wallet_index`; khóa gốc được giữ trong bản sao lưu mã hóa ngoài server.

Ví operator riêng giữ `OPERATOR_ROLE`, trả gas và ký mint/burn sau khi API xác thực điều kiện và người dùng. Đây là mô hình lưu ký: operator có quyền tiêu token của ví quản lý thông qua `redeem`; việc xác thực/ủy quyền người dùng thuộc backend. Không quảng bá là ví tự lưu ký. Token không hỗ trợ chuyển, allowance, mua bán, rút tiền hoặc giao dịch trên sàn.

Khi đã bật worker, ví operator phải dành riêng cho worker; không dùng công cụ khác gửi giao dịch từ cùng ví vì có thể chiếm hoặc thay thế nonce đang xử lý.

Contract dùng OpenZeppelin 5.4.0, phân quyền admin/operator, đổi admin qua bước chờ 2 ngày, pause/unpause, trần phát hành trọn đời 1 tỷ SC. Mỗi mint tối đa 1.000 SC; mỗi burn tối đa 10.000 SC. `operationId` duy nhất chống mint/burn trùng, kể cả khi worker ký lại sau lỗi mạng.

## Kiểm thử cục bộ

Chạy từ thư mục dự án:

```powershell
npm.cmd ci
npm.cmd --prefix blockchain ci
npm.cmd --prefix blockchain test
npm.cmd --prefix blockchain run compile
node supabase/functions/studymind-api/build.mjs
```

Test dùng PostgreSQL thực qua PGlite và EVM cục bộ Ganache, không cần private key hay token công khai. Các tài khoản/mnemonic trong test đều là dữ liệu công khai cố định, chỉ dùng trên EVM cô lập. Database EVM tạm được tạo trong workspace và dọn sau mỗi test. Ganache là công cụ phát triển; không đóng gói hoặc triển khai nó vào backend/Edge Functions. Trên Node mới có thể xuất hiện thông báo dùng JavaScript thay native µWS; không ảnh hưởng logic kiểm thử.

## Chain nội bộ (không cần POL testnet)

Dùng khi chưa xin được POL Amoy. Chain EVM riêng (chain ID 31337) chạy trên máy, dữ liệu giữ trong `.local/chain/`; SC ở đây không có giá trị ngoài máy này. Backend Node chạy local vì Supabase Edge Function trên cloud không gọi được `127.0.0.1`.

1. Cửa sổ 1, giữ chạy: `node blockchain/scripts/local-chain.mjs`. Lần đầu tạo `.secrets/local-chain.json` (mnemonic chain, khóa admin/operator, xpub ví người học; chỉ dùng nội bộ, không dùng trên mạng công khai). Xóa file này sẽ làm chain cũ không dùng được.
2. Cửa sổ 2: `node blockchain/scripts/local-setup.mjs`. Deploy StudyCoin một lần (chạy lại thì dùng lại contract) và ghi `.local/rewards-local.env`.
3. Điền `SUPABASE_ANON_KEY` và `SUPABASE_SERVICE_ROLE_KEY` của project **staging** vào `.local/rewards-local.env`. Không dùng key production.
4. Ghi cấu hình vào database staging: `node --env-file=.local/rewards-local.env blockchain/scripts/configure-staging.mjs`.
5. Backend: `node --env-file=.local/rewards-local.env backend/server.js` (cổng 5000). Worker, chạy lặp mỗi phút: `node --env-file=.local/rewards-local.env backend/rewards-worker.js`.
6. App Android debug: emulator dùng `-PapiBase=http://10.0.2.2:5000`; điện thoại thật cắm USB chạy `adb reverse tcp:5000 tcp:5000` rồi dùng `-PapiBase=http://127.0.0.1:5000`. Kèm `-PsupabaseUrl`/`-PsupabaseAnonKey` của staging.
7. Kiểm tra: `node --env-file=.local/rewards-local.env blockchain/scripts/status.mjs`.

Database staging chỉ gắn với một mạng: khi đã có ví người học trên chain nội bộ, chuyển sang Amoy cần đối soát/khởi tạo lại dữ liệu staging (`configure-staging.mjs` sẽ từ chối).

## Bật trên staging/Amoy

**Chưa có contract Amoy hoặc backend staging được triển khai bởi thay đổi mã nguồn này.** Không dùng dữ liệu test hoặc seed test để quản lý tài sản thật. Script triển khai chỉ cho testnet công khai Polygon Amoy (80002) hoặc Ethereum Sepolia (11155111), tự nhận mạng theo `REWARDS_RPC_URL`; backend chấp nhận 80002, 11155111 và chain nội bộ 31337. Sepolia cần thêm migration `008_reward_sepolia.sql`. Hướng dẫn từng bước cho Sepolia: [docs/huong-dan-chay-online-sepolia.md](../docs/huong-dan-chay-online-sepolia.md).

1. Chuẩn bị Supabase staging có schema StudyMind nền (001–004). Áp dụng lần lượt migrations `005_blockchain_rewards.sql`, `006_reward_question_bank.sql`, `007_reward_admin.sql`. Migration mới mặc định tắt phần thưởng. Không chạy lại migration đã áp dụng.
2. Tạo bộ ví quản lý và sao lưu. Đặt biến `STUDYMIND_VAULT_PASSWORD` riêng với mật khẩu mạnh, chạy `node blockchain/scripts/create-wallet-vault.mjs`. Script lưu keystore mã hóa dưới `.secrets/` (bị Git bỏ qua, không ghi đè file cũ), chỉ in xpub và fingerprint. Sao lưu keystore và mật khẩu riêng biệt; xóa biến mật khẩu khi xong. Không gửi mật khẩu/seed cho người dùng hay ghi log.
3. Chuẩn bị ví admin và operator **riêng** cho testnet; nạp POL testnet cho deployer/operator. Đặt các biến trong `.env.rewards.example` tại môi trường thích hợp. Script deploy cần `REWARDS_RPC_URL`, `REWARDS_DEPLOYER_KEY`, `REWARDS_ADMIN_ADDRESS`, `REWARDS_OPERATOR_ADDRESS`.
4. Chạy `node blockchain/scripts/deploy.mjs` từ root. Script ghi bản ghi công khai `blockchain/deployments/amoy-<contract>.json` (địa chỉ, transaction, block, compiler, hash bytecode, constructor arguments) và `amoy-<contract>.verify.json` (Standard JSON input đã gộp mã OpenZeppelin) để xác minh mã nguồn trên Amoy Polygonscan: chọn *Solidity (Standard-Json-Input)*, compiler trong bản ghi, dán constructor arguments. Commit các file này; chúng không chứa khóa. Khi đã có bản ghi, script từ chối deploy lại trừ khi đặt `REWARDS_ALLOW_REDEPLOY=true` cho một contract mới có kế hoạch.
5. Đặt `REWARDS_TOKEN_ADDRESS`, `REWARDS_WALLET_XPUB`, `REWARDS_OPERATOR_KEY`, URL/service key staging và `REWARDS_STAGING_PROJECT_REF`; chạy `node blockchain/scripts/configure-staging.mjs`. Script kiểm tra mạng, runtime bytecode, quyền operator và ràng buộc ví trước khi ghi cấu hình. Không đổi xpub/contract/network của ví đã tồn tại bằng cách sửa config trực tiếp.
6. Build `studymind-api` bằng `node supabase/functions/studymind-api/build.mjs`, triển khai bundle lên staging. Hàm hiện tự kiểm tra Supabase JWT nên gateway Verify JWT giữ cấu hình phù hợp như deployment hiện tại.
7. Server secrets cần: `SUPABASE_URL`, service key (Supabase cung cấp), `REWARDS_ENABLED=true`, `REWARDS_RPC_URL`, `REWARDS_WALLET_XPUB`, `REWARDS_OPERATOR_KEY`, `REWARDS_WORKER_SECRET` ngẫu nhiên ít nhất 32 byte, `REWARDS_CONFIRMATIONS=64`. Không đưa deployer key, keystore hay vault password lên server. Không đưa bất kỳ key riêng nào vào `BuildConfig`.
8. Cấu hình scheduler đáng tin cậy gọi **POST** tới `/functions/v1/studymind-api/api/internal/rewards/run` mỗi phút, header `x-rewards-worker-secret` lấy từ secret store. Route này không dùng JWT người dùng; secret không hợp lệ bị từ chối. Lựa chọn thay thế là chạy `node backend/rewards-worker.js` mỗi phút trên máy chủ riêng; chỉ cần chọn một scheduler, lease bảo vệ nếu có chạy chồng.
9. App debug hiện vẫn dùng Supabase project cũ trong `app/build.gradle`. Muốn thử trên staging phải cấu hình **đồng bộ** Supabase URL, publishable key và API base bằng Gradle properties được hướng dẫn trong `android-native/README.md`; đổi riêng API base là không đủ vì JWT khác project.
10. Cấu hình trang mời: deploy `public/referral.html` và `public/referral-config.json` lên web URL đã cấu hình trong app. Điền URL HTTPS tải APK nếu có. Bản thử nghiệm hỗ trợ nhập mã và nút mở app qua custom scheme; chưa triển khai Play Install Referrer hoặc verified HTTPS App Links. Bổ sung hai phần đó khi chốt phát hành Google Play/domain/chứng chỉ ký.

Chỉ bật cờ cho nhóm thử sau khi chạy kiểm thử tích hợp trên staging. Mainnet, phát hành Play và thay luồng thanh toán SePay không nằm trong các script bật thử nghiệm này.

## Máy trạng thái và xử lý sự cố

`queued → prepared → submitted → confirmed` hoặc `failed`. Chỉ `confirmed` là giao dịch đã đối soát. App phân biệt token đã xác nhận, mint đang chờ và burn đang chờ. RPC lỗi trả số dư không khả dụng, không bịa số dư 0.

- Worker giữ lease 120 giây, chỉ xử lý tối đa khoảng 45 giây mỗi lượt giữa các công việc. Signed transaction/hash/nonce được lưu **trước khi broadcast**; timeout phát lại cùng byte, không tự mint thêm.
- Giao dịch rơi khỏi mempool được phát lại từ dữ liệu đã lưu. Nếu nonce đã bị tiêu trên canonical chain nhưng operation chưa tồn tại, worker có thể xếp lại cùng operation ID. Giao dịch bị kẹt phí vẫn cần theo dõi/vận hành; bản thử nghiệm chưa có cơ chế tự tăng phí.
- Worker kiểm tra chain ID, contract, ví, lượng token, loại thao tác, số ngày, receipt thành công, block hash canonical và ít nhất **64 xác nhận** trên Amoy. Đây là chính sách độ sâu xác nhận, không phải bảo đảm tuyệt đối trước mọi reorg sâu.
- Burn thành công nhưng ghi database lỗi: giữ trạng thái chờ, lượt worker sau xác minh receipt và cấp Pro trong transaction SQL; không burn lại. Cấp Pro và cập nhật job/đơn đổi cùng transaction, gọi lại không cộng thêm ngày.
- Revert không cấp Pro. Quản trị có thể gửi lại job thất bại sau khi sửa nguyên nhân; chỉ cho retry các lỗi đã biết không thực hiện token. Không cho “bỏ qua blockchain để cấp Pro”.
- Không thu hồi/hoàn tác giao dịch chain đã thành công bằng cách sửa status SQL. Sự cố reorg sâu hoặc lộ khóa cần tắt `REWARDS_ENABLED`, pause contract nếu phù hợp, giữ nguyên dữ liệu và lập phương án đối soát trước khi xử lý.
- Trước mainnet cần kiểm tra bảo mật độc lập, cơ chế giám sát/cảnh báo, mở rộng ngân hàng câu hỏi, chống cụm tài khoản giả, ngân sách phát thưởng và kế hoạch khôi phục khóa. Ngân hàng 45 câu hiện phục vụ kiểm thử sản phẩm, chưa phải hệ thống thi chống gian lận chuyên dụng.

## Giám sát

`node blockchain/scripts/status.mjs` chỉ đọc, không cần private key. Cần `REWARDS_RPC_URL`, `REWARDS_TOKEN_ADDRESS`; thêm `REWARDS_OPERATOR_ADDRESS` để kiểm tra quyền, gas, nonce của operator; thêm `SUPABASE_URL` + service key để đọc hàng đợi (chỉ status/thời gian/mã lỗi, không đọc `raw_transaction`). Có cảnh báo thì exit code `2`, dùng được cho scheduler/uptime monitor.

| Cảnh báo | Ý nghĩa |
|---|---|
| `BYTECODE_MISMATCH` | Địa chỉ không chạy đúng mã StudyCoin đã review |
| `CONTRACT_PAUSED` | Mint/burn đang dừng |
| `OPERATOR_ROLE_MISSING` / `OPERATOR_LOW_GAS` | Operator mất quyền hoặc sắp hết gas (dưới 0,1 POL trên Amoy, 0,005 ETH trên Sepolia) |
| `ADMIN_TRANSFER_PENDING` | Có yêu cầu đổi admin đang chờ — xác nhận đó là thao tác có kế hoạch |
| `CAP_NEARLY_REACHED` | Còn dưới 1% trần phát hành trọn đời |
| `JOBS_STUCK` / `JOBS_FAILED` | Job chờ quá 20 phút hoặc có job thất bại cần xem |

## Quản trị contract

`node blockchain/scripts/admin.mjs <lệnh> [địa chỉ]` cần `REWARDS_RPC_URL`, `REWARDS_TOKEN_ADDRESS`, `REWARDS_ADMIN_KEY` (khóa admin A; với `accept-admin` là khóa admin mới). Mặc định chỉ in kế hoạch; thêm `--yes` mới gửi giao dịch. Script kiểm tra mạng Amoy, bytecode, quyền người ký và điều kiện trước khi gửi.

| Lệnh | Khi dùng |
|---|---|
| `pause` / `unpause` | Sự cố, lộ khóa operator, reorg sâu; mở lại sau khi đối soát |
| `grant-operator` / `revoke-operator` | Thay ví operator |
| `begin-admin-transfer`, `cancel-admin-transfer`, `accept-admin` | Chuyển quyền admin, chờ đủ 2 ngày |

**Thay operator:** `pause` → dừng worker → chờ không còn job `prepared`/`submitted` chưa xác nhận (giao dịch đã ký bằng khóa cũ sẽ bị từ chối với khóa mới) → `grant-operator <mới>` → `revoke-operator <cũ>` → đổi `REWARDS_OPERATOR_KEY` ở server → `unpause` → chạy worker. Nếu khóa cũ bị lộ, `revoke-operator` ngay sau `pause`; job bị revert do mất quyền có thể được admin retry sau khi đổi khóa.

## Học trong bài học cũng được tính

Migration `009_lesson_reward_evidence.sql`: API ghi bằng chứng qua `studymind_record_lesson_event` (chỉ `service_role`) khi người học ôn thẻ hoặc nộp quiz trong bài học thường. Chỉ tính nội dung do AI tạo (thẻ/câu hỏi tự thêm có cờ `userCreated` hoặc id `fc-/q-<timestamp>`); mỗi thẻ 1 lần/ngày và cách nhau ≥3 giây; quiz cần ≥10 câu, trả lời đủ, chỉ lần nộp đầu tiên mỗi bài mỗi ngày. Pomodoro trên Android mở phiên tập trung của server và gửi heartbeat mỗi phút. "Làm chủ 3 chủ đề" và điều kiện quiz giới thiệu chỉ tính nguồn `quiz:<phiên thử thách>`.

## Mức thưởng giữ theo kế hoạch đã duyệt

Ngày: 5 SC bắt đầu học, 10 SC ôn 10 thẻ, 20 SC quiz ≥80%, 10 SC tập trung 25 phút; tổng 45 SC. Chuỗi 7 ngày: 60 SC; làm chủ 3 chủ đề/tuần: 40 SC. Giới thiệu: 200/30 SC sau 2 ngày học, 20 thẻ, quiz ≥80%, giữ 48 giờ; tối đa 3 lượt/tháng/người mời.

Đổi Pro 1/7/30 ngày: 100/600/2.400 SC. Hạn được cộng từ `max(now, pro_until)`; không tự gia hạn, không cộng vào doanh thu SePay. Admin có thể thay mức thưởng/giá trong giới hạn; nhiệm vụ đã giao giữ snapshot cũ. Không chuyển dữ liệu gamification cũ thành token.

## Tài liệu tham chiếu

- [Ethers v6 — Wallets](https://docs.ethers.org/v6/api/wallet/).
- [OpenZeppelin ERC-20](https://docs.openzeppelin.com/contracts/5.x/erc20) và [Access Control](https://docs.openzeppelin.com/contracts/5.x/access-control).
- [Polygon Amoy RPC và chain ID](https://docs.polygon.technology/pos/reference/rpc-endpoints).
- [Google Play — Blockchain-based Content](https://support.google.com/googleplay/android-developer/answer/13607354?hl=en): cần đối chiếu khai báo tài sản token hóa trước khi đưa lên Play; chưa có kết luận chấp thuận cửa hàng cho bản thử nghiệm.
