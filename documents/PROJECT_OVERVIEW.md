---
title: "Nền Tảng Thương Mại Điện Tử Đa Người Bán"
date: "2026-05-15"
---

# NỀN TẢNG THƯƠNG MẠI ĐIỆN TỬ ĐA NGƯỜI BÁN

---

## BỐI CẢNH VÀ LÝ DO THỰC HIỆN ĐỀ TÀI

### 1. Bối cảnh và nhu cầu thực tế

Thương mại điện tử tại Việt Nam ghi nhận mức tăng trưởng bình quân **25–30%/năm** giai đoạn 2020–2025, với hơn 60 triệu người dùng internet thường xuyên mua sắm trực tuyến. Trong bối cảnh đó, hầu hết người bán nhỏ và vừa vẫn đang phụ thuộc hoàn toàn vào các nền tảng thương mại điện tử đóng như Shopee, Tiki, Lazada — những nền tảng áp đặt mức hoa hồng cao, kiểm soát toàn bộ dữ liệu khách hàng, và hạn chế khả năng tùy biến vận hành của người bán.

**Về phía người bán**, các vấn đề nổi cộm bao gồm:

- **Phụ thuộc và thiếu kiểm soát:** Người bán buộc phải chấp nhận chính sách phí, thuật toán hiển thị và quy tắc vận hành từ sàn mà không có quyền thương lượng. Mọi thay đổi từ phía sàn đều ảnh hưởng trực tiếp đến doanh thu mà không có cơ chế phòng ngừa.
- **Quản lý tồn kho thiếu đồng bộ:** Khi bán trên nhiều kênh, tồn kho không được đồng bộ tự động dẫn đến tình trạng bán quá số lượng, gây khiếu nại và hủy đơn hàng loạt.
- **Không có công cụ flash sale độc lập:** Chương trình giảm giá theo khung giờ phải đăng ký qua sàn, chịu phí vị trí và mất quyền kiểm soát thời gian, số lượng, mức giảm.
- **Thanh toán chậm và thiếu minh bạch:** Dòng tiền bị giữ lại tại sàn trung gian, thời gian giải ngân kéo dài 7–14 ngày, không có cơ chế phân chia tự động khi một đơn hàng chứa sản phẩm của nhiều người bán.
- **Mất quyền sở hữu dữ liệu khách hàng:** Toàn bộ hành vi mua sắm, lịch sử tìm kiếm và thông tin liên hệ của khách hàng thuộc về sàn, người bán không thể xây dựng tệp khách hàng trung thành riêng.

**Về phía người mua**, trải nghiệm mua sắm trực tuyến hiện tại vẫn tồn tại nhiều điểm yếu:

- **Tìm kiếm bị chi phối bởi quảng cáo:** Kết quả tìm kiếm ưu tiên sản phẩm trả phí thay vì phản ánh nhu cầu thực. Riêng với tiếng Việt, sự khác biệt giữa từ có dấu và không dấu thường dẫn đến kết quả không liên quan.
- **Flash sale thiếu minh bạch:** Người mua không biết số lượng tồn kho thực tế còn lại, không xác minh được giá gốc trước khi giảm, và dễ bị lẫn lộn giữa chương trình giảm giá thực sự và chiêu marketing.
- **Không có hỗ trợ thông minh:** Việc tìm sản phẩm phù hợp với nhu cầu cụ thể hoàn toàn phụ thuộc vào khả năng lọc thủ công của người dùng, không có trợ lý tư vấn nào can thiệp.
- **Quy trình hoàn trả không rõ ràng:** Yêu cầu hoàn tiền trải qua nhiều bước thủ công, người mua không có cách theo dõi tiến độ một cách chính xác và nhất quán.

**Về phía hệ thống**, kiến trúc monolith của các ứng dụng truyền thống không đáp ứng được nhu cầu scale theo từng nghiệp vụ, đặc biệt trong các sự kiện flash sale có lưu lượng truy cập đột biến. Một lỗi tại một module có thể làm sập toàn bộ hệ thống, và không có audit trail đầy đủ để truy vết sự cố.

---

### 2. Điểm mới của đề tài

So với các hệ thống thương mại điện tử truyền thống, đề tài này mang lại các điểm mới trên cả hai chiều **kiến trúc kỹ thuật** và **nghiệp vụ**:

#### Về kiến trúc kỹ thuật

**Kiến trúc Microservices thuần nhất với 10 service độc lập.** Mỗi domain nghiệp vụ (Identity, Product, Order, Payment, Flash Sale, Search, Notification, AI Chat) được tách thành service riêng với cơ sở dữ liệu riêng, cho phép scale độc lập, triển khai độc lập và cô lập lỗi hoàn toàn. Các service giao tiếp qua Kafka (58 topics) và API Gateway duy nhất.

**Xử lý đơn hàng và thanh toán theo mô hình CQRS/Event Sourcing với Axon Framework.** Toàn bộ lịch sử trạng thái của đơn hàng và giao dịch được lưu dưới dạng chuỗi sự kiện bất biến, cho phép replay lại để khôi phục dữ liệu, audit đầy đủ, và tách biệt luồng đọc/ghi để tối ưu hiệu suất.

**Saga Pattern cho checkout đa service.** Quá trình thanh toán liên quan đến Product Service (đặt trước tồn kho), Order Service (tạo đơn), Payment Service (xử lý Stripe) được điều phối bởi Saga, đảm bảo tính nhất quán phân tán mà không cần lock toàn bộ hệ thống.

**Flash Sale với đồng bộ giá qua Kafka** Session flash sale được quản lý với Redis ZSET cho transition trạng thái, giá flash sale được đồng bộ qua Kafka tới Search Service và tồn kho được xử lý bởi Checkout Service.

**Tìm kiếm tiếng Việt với Elasticsearch và ICU Analyzer.** Hệ thống xây dựng pipeline phân tích văn bản riêng cho tiếng Việt, xử lý đúng từ có dấu/không dấu, tên riêng, và từ ghép — vốn là điểm yếu của full-text search thông thường.

#### Về nghiệp vụ

**Thanh toán đa người bán tự động qua Stripe Connect.** Một đơn hàng có thể chứa sản phẩm từ nhiều người bán khác nhau. Hệ thống tự động phân chia và chuyển khoản cho từng người bán sau khi người mua xác nhận nhận hàng, không cần xử lý thủ công.

**Trợ lý mua sắm AI tích hợp sâu vào nghiệp vụ.** Không chỉ trả lời câu hỏi, AI Chat Service có khả năng gọi trực tiếp các công cụ nội bộ (tìm sản phẩm, đặt đơn, kiểm tra trạng thái) thông qua tool calls. Các hành động không thể đảo ngược (đặt đơn, xác nhận thanh toán) đều yêu cầu xác nhận của người dùng trước khi thực thi — cơ chế *human-in-the-loop* đảm bảo an toàn nghiệp vụ.

**Thông báo real-time đa loại sự kiện qua SSE.** Thay vì polling định kỳ, mọi thay đổi trạng thái (đơn hàng, thanh toán, flash sale) đều được đẩy tức thì đến đúng người dùng thông qua Server-Sent Events, không mất dữ liệu ngay cả khi nhiều sự kiện xảy ra cùng lúc.

**Quy trình hoàn trả (Return To Sender) có cấu trúc rõ ràng.** Luồng RTS đi qua ba bên (Buyer → Seller → Admin) với trạng thái minh bạch tại từng bước, kết thúc bằng lệnh hoàn tiền tự động về đúng nguồn thanh toán qua Stripe.

---

### 3. Mục đích

Đề tài được thực hiện với ba mục đích chính:

**Mục đích học thuật —** Nghiên cứu và hiện thực hóa các mô hình kiến trúc phần mềm hiện đại trong một hệ thống thực tế quy mô lớn, bao gồm: Microservices, CQRS/Event Sourcing, Saga Pattern, event-driven architecture với Kafka, và tích hợp AI vào quy trình nghiệp vụ. Đề tài cũng là minh chứng thực nghiệm cho việc áp dụng đồng thời nhiều mô hình kiến trúc trong một hệ thống tích hợp.

**Mục đích kỹ thuật —** Xây dựng một nền tảng thương mại điện tử đa người bán có khả năng:

- Xử lý flash sale với lưu lượng đột biến mà không xảy ra mất dữ liệu hay oversell
- Đảm bảo tính nhất quán của giao dịch phân tán qua nhiều service mà không dùng distributed lock
- Cung cấp audit trail đầy đủ cho toàn bộ vòng đời đơn hàng và giao dịch
- Mở rộng từng domain độc lập theo nhu cầu tải thực tế

**Mục đích thực tiễn —** Tạo ra một giải pháp thay thế khả thi cho người bán nhỏ và vừa tại Việt Nam muốn vận hành kênh bán hàng trực tuyến độc lập, thoát khỏi sự phụ thuộc vào các sàn thương mại điện tử lớn, đồng thời cung cấp trải nghiệm mua sắm tốt hơn cho người dùng cuối thông qua tìm kiếm chính xác, flash sale minh bạch và hỗ trợ AI.
