interface FooterProps {
  appName?: string;
}

export default function Footer({ appName = 'FlashSale' }: FooterProps) {
  return (
    <footer className="bg-gray-950 text-gray-400 mt-auto">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-2 lg:grid-cols-3 gap-8 pb-8 border-b border-gray-800">
          {/* Brand */}
          <div className="sm:col-span-2 md:col-span-2 lg:col-span-1">
            <div className="flex items-center gap-2 mb-3">
              <span className="flex items-center justify-center w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-violet-600 text-white text-lg font-bold">
                ⚡
              </span>
              <span className="text-white font-bold text-lg">{appName}</span>
            </div>
            <p className="text-sm leading-relaxed">
              Nền tảng mua sắm flash sale hàng đầu Việt Nam. Giá tốt nhất, giao hàng nhanh nhất.
            </p>
          </div>

          {/* Links */}
          <div>
            <h4 className="text-white text-sm font-semibold mb-3 uppercase tracking-wider">Hỗ trợ</h4>
            <ul className="space-y-2 text-sm">
              {['Trung tâm trợ giúp', 'Chính sách hoàn tiền', 'Liên hệ chúng tôi', 'Điều khoản dịch vụ'].map((item) => (
                <li key={item}>
                  <span className="hover:text-white cursor-pointer transition-colors">{item}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Legal */}
          <div>
            <h4 className="text-white text-sm font-semibold mb-3 uppercase tracking-wider">Pháp lý</h4>
            <ul className="space-y-2 text-sm">
              {['Chính sách bảo mật', 'Cookie', 'Điều khoản sử dụng', 'Báo cáo vi phạm'].map((item) => (
                <li key={item}>
                  <span className="hover:text-white cursor-pointer transition-colors">{item}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="pt-6 flex flex-col md:flex-row items-center justify-between gap-3 text-sm">
          <p>&copy; {new Date().getFullYear()} {appName}. Mọi quyền được bảo lưu.</p>
          <div className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-green-400 inline-block animate-pulse" />
            <span className="text-xs">All systems operational</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
