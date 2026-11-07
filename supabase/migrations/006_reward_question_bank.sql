-- Private starter bank. It is intentionally separate from user-editable lessons.
begin;
with bank(topic,question,a,b,c,d) as (values
 ('Toán học','Nếu 3x + 5 = 20 thì x bằng bao nhiêu?','5','3','7','10'),
 ('Toán học','Diện tích hình chữ nhật dài 8 cm, rộng 5 cm là bao nhiêu?','40 cm²','26 cm²','13 cm²','80 cm²'),
 ('Toán học','25% của 200 là bao nhiêu?','50','25','75','100'),
 ('Toán học','Số nguyên tố nào nằm giữa 20 và 25?','23','21','22','24'),
 ('Toán học','Trung bình cộng của 4, 8, 12 là bao nhiêu?','8','6','10','12'),
 ('Toán học','Căn bậc hai số học của 144 bằng bao nhiêu?','12','14','72','24'),
 ('Toán học','Tổng ba góc trong một tam giác bằng bao nhiêu?','180 độ','90 độ','270 độ','360 độ'),
 ('Toán học','Giá trị của 2 mũ 5 là bao nhiêu?','32','10','16','25'),
 ('Toán học','Phân số 3/4 viết dưới dạng số thập phân là gì?','0,75','0,34','0,25','1,33'),
 ('Toán học','Một vật đi 60 km trong 2 giờ. Vận tốc trung bình là bao nhiêu?','30 km/h','60 km/h','120 km/h','20 km/h'),
 ('Toán học','Chu vi hình vuông cạnh 7 cm bằng bao nhiêu?','28 cm','49 cm','14 cm','21 cm'),
 ('Toán học','Giá trị tuyệt đối của -9 bằng bao nhiêu?','9','-9','0','18'),
 ('Toán học','Ước chung lớn nhất của 12 và 18 là bao nhiêu?','6','3','12','36'),
 ('Toán học','Tung một đồng xu cân đối một lần, xác suất ra mặt ngửa là bao nhiêu?','1/2','1/4','1','0'),
 ('Toán học','Một món hàng 100.000 đồng giảm 10%. Giá sau giảm là bao nhiêu?','90.000 đồng','10.000 đồng','99.000 đồng','110.000 đồng'),
 ('Khoa học','Công thức hóa học của nước là gì?','H₂O','CO₂','O₂','NaCl'),
 ('Khoa học','Cơ quan nào chủ yếu thực hiện trao đổi khí ở người?','Phổi','Dạ dày','Thận','Gan'),
 ('Khoa học','Đơn vị đo cường độ dòng điện trong hệ SI là gì?','Ampe','Vôn','Ôm','Oát'),
 ('Khoa học','Hành tinh nào gần Mặt Trời nhất?','Sao Thủy','Sao Kim','Trái Đất','Sao Hỏa'),
 ('Khoa học','Khí nào chiếm tỉ lệ lớn nhất trong không khí khô của Trái Đất?','Nitơ','Oxy','Carbon dioxide','Hydro'),
 ('Khoa học','Cây xanh hấp thụ khí nào chủ yếu trong quá trình quang hợp?','Carbon dioxide','Oxy','Nitơ','Heli'),
 ('Khoa học','Lực nào giữ các hành tinh chuyển động quanh Mặt Trời?','Lực hấp dẫn','Lực ma sát','Lực đàn hồi','Lực đẩy Archimedes'),
 ('Khoa học','Nước tinh khiết sôi ở khoảng nhiệt độ nào tại áp suất khí quyển tiêu chuẩn?','100°C','0°C','50°C','200°C'),
 ('Khoa học','Bộ phận nào của tế bào nhân thực thường chứa phần lớn DNA?','Nhân tế bào','Màng tế bào','Ribosome','Không bào'),
 ('Khoa học','Âm thanh không truyền được trong môi trường nào?','Chân không','Không khí','Nước','Thép'),
 ('Khoa học','Kim loại nào là chất dẫn điện tốt trong các lựa chọn sau?','Đồng','Cao su','Thủy tinh','Nhựa'),
 ('Khoa học','Đơn vị đo lực trong hệ SI là gì?','Newton','Joule','Watt','Pascal'),
 ('Khoa học','Hồng cầu có vai trò chính nào?','Vận chuyển oxy','Tiêu hóa thức ăn','Lọc nước tiểu','Tạo xương'),
 ('Khoa học','Quá trình chất lỏng chuyển thành chất khí ở bề mặt được gọi là gì?','Bay hơi','Đông đặc','Ngưng tụ','Nóng chảy'),
 ('Khoa học','Nguồn năng lượng nào sau đây là năng lượng tái tạo?','Ánh sáng Mặt Trời','Than đá','Dầu mỏ','Khí tự nhiên'),
 ('Tiếng Việt','Từ nào là danh từ?','Học sinh','Chạy','Nhanh','Đẹp'),
 ('Tiếng Việt','Từ trái nghĩa với “chăm chỉ” là gì?','Lười biếng','Siêng năng','Cần cù','Chịu khó'),
 ('Tiếng Việt','Dấu câu nào thường kết thúc một câu hỏi?','Dấu chấm hỏi','Dấu phẩy','Dấu hai chấm','Dấu chấm phẩy'),
 ('Tiếng Việt','Trong câu “Lan đọc sách”, chủ ngữ là gì?','Lan','Đọc','Sách','Đọc sách'),
 ('Tiếng Việt','Từ nào là từ láy?','Lung linh','Học tập','Sách vở','Nhà cửa'),
 ('Tiếng Việt','“Nhanh như chớp” sử dụng biện pháp tu từ nào?','So sánh','Liệt kê','Đảo ngữ','Nói giảm'),
 ('Tiếng Việt','Từ nào đồng nghĩa với “dũng cảm”?','Can đảm','Nhút nhát','Lo sợ','Rụt rè'),
 ('Tiếng Việt','Trong câu “Bầu trời rất xanh”, vị ngữ là gì?','Rất xanh','Bầu trời','Trời','Bầu'),
 ('Tiếng Việt','Từ nào là động từ?','Bơi','Xanh','Bàn','Rộng'),
 ('Tiếng Việt','“Mặt trời mỉm cười” sử dụng biện pháp tu từ nào?','Nhân hóa','Liệt kê','Chơi chữ','Đảo ngữ'),
 ('Tiếng Việt','Từ trái nghĩa với “trung thực” là gì?','Gian dối','Thật thà','Thẳng thắn','Chân thành'),
 ('Tiếng Việt','Câu “Bạn hãy đóng cửa lại.” thuộc kiểu câu nào theo mục đích nói?','Cầu khiến','Nghi vấn','Trần thuật','Cảm thán'),
 ('Tiếng Việt','Từ nào là tính từ?','Hiền lành','Đọc','Bút','Chạy'),
 ('Tiếng Việt','Trong “Chúng em học để tiến bộ”, “để tiến bộ” thể hiện ý nghĩa gì?','Mục đích','Nguyên nhân','Thời gian','Địa điểm'),
 ('Tiếng Việt','“Ăn quả nhớ kẻ trồng cây” nhắc nhở điều gì?','Biết ơn người tạo ra thành quả','Chỉ nên ăn trái cây','Trồng cây thật nhanh','Tránh giúp đỡ người khác')
)
insert into public.studymind_reward_questions(topic,question,options,answer)
select b.topic,b.question,s.options,s.answer from bank b cross join lateral (
 select jsonb_agg(v order by rn) as options,max((rn-1)::int) filter(where v=b.a) as answer
 from (select v,row_number() over(order by random()) rn from unnest(array[b.a,b.b,b.c,b.d]) v) shuffled
) s;
commit;
