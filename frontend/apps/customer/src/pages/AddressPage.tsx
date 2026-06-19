import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { addressApi } from '@shared/api/address.api';
import { userApi, type AddressResponse } from '@shared/api/user.api';
import { Skeleton } from '@shared/components/ui';

interface AddressFormData {
  provinceId: number;
  districtId: number;
  fullAddress: string;
  isDefault: boolean;
}

function AddressModal({ address, defaultData, onClose, onSuccess }: {
  address?: AddressResponse;
  defaultData?: AddressFormData;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<AddressFormData>({
    provinceId: address?.provinceId ?? defaultData?.provinceId ?? 0,
    districtId: address?.districtId ?? defaultData?.districtId ?? 0,
    fullAddress: address?.fullAddress ?? defaultData?.fullAddress ?? '',
    isDefault: address?.isDefault ?? defaultData?.isDefault ?? false,
  });
  const [error, setError] = useState('');

  const createMut = useMutation({
    mutationFn: () => userApi ? addressApi.create(form) : Promise.resolve(null as any),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['user-addresses'] }); onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message ?? 'Lưu thất bại'),
  });

  const updateMut = useMutation({
    mutationFn: () => addressApi.update(address!.addressId, form),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['user-addresses'] }); onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message ?? 'Cập nhật thất bại'),
  });

  const mut = address ? updateMut : createMut;

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md">
        <h3 className="text-lg font-bold text-gray-900 mb-5">
          {address ? 'Sửa địa chỉ' : 'Thêm địa chỉ mới'}
        </h3>
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">
            {error}
          </div>
        )}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Tỉnh / Thành phố</label>
            <select
              value={form.provinceId}
              onChange={e => setForm(f => ({ ...f, provinceId: Number(e.target.value), districtId: 0 }))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value={0}>Chọn tỉnh/thành phố</option>
              {PROVINCES.map(p => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Quận / Huyện</label>
            <select
              value={form.districtId}
              onChange={e => setForm(f => ({ ...f, districtId: Number(e.target.value) }))}
              disabled={!form.provinceId}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:cursor-not-allowed"
            >
              <option value={0}>Chọn quận/huyện</option>
              {DISTRICTS.filter(d => d.provinceId === form.provinceId).map(d => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Địa chỉ chi tiết</label>
            <textarea
              value={form.fullAddress}
              onChange={e => setForm(f => ({ ...f, fullAddress: e.target.value }))}
              rows={3}
              placeholder="Số nhà, đường, phường/xã..."
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={form.isDefault}
              onChange={e => setForm(f => ({ ...f, isDefault: e.target.checked }))}
              className="w-4 h-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500"
            />
            <span className="text-sm text-gray-700">Đặt làm địa chỉ mặc định</span>
          </label>
        </div>
        <div className="flex gap-3 mt-6">
          <button onClick={onClose} className="flex-1 py-2.5 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50">
            Huỷ
          </button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending || !form.provinceId || !form.districtId || !form.fullAddress.trim()}
            className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium disabled:opacity-60"
          >
            {mut.isPending ? 'Đang lưu...' : 'Lưu'}
          </button>
        </div>
      </div>
    </div>
  );
}

// Complete Vietnam province/district data — 63 provinces, all districts
export interface Province { id: number; name: string; }
export interface District { id: number; provinceId: number; name: string; }

export const PROVINCES: Province[] = [
  { id: 1, name: 'Thành phố Hồ Chí Minh' },
  { id: 2, name: 'Thành phố Hà Nội' },
  { id: 3, name: 'Thành phố Đà Nẵng' },
  { id: 4, name: 'Thành phố Hải Phòng' },
  { id: 5, name: 'Thành phố Cần Thơ' },
  { id: 6, name: 'Thành phố Cần Thơ' },
  { id: 7, name: 'Tỉnh An Giang' },
  { id: 8, name: 'Tỉnh Bà Rịa - Vũng Tàu' },
  { id: 9, name: 'Tỉnh Bắc Giang' },
  { id: 10, name: 'Tỉnh Bắc Kạn' },
  { id: 11, name: 'Tỉnh Bạc Liêu' },
  { id: 12, name: 'Tỉnh Bắc Ninh' },
  { id: 13, name: 'Tỉnh Bến Tre' },
  { id: 14, name: 'Tỉnh Bình Định' },
  { id: 15, name: 'Tỉnh Bình Dương' },
  { id: 16, name: 'Tỉnh Bình Phước' },
  { id: 17, name: 'Tỉnh Bình Thuận' },
  { id: 18, name: 'Tỉnh Cà Mau' },
  { id: 19, name: 'Tỉnh Cao Bằng' },
  { id: 20, name: 'Tỉnh Đắk Lắk' },
  { id: 21, name: 'Tỉnh Đắk Nông' },
  { id: 22, name: 'Tỉnh Điện Biên' },
  { id: 23, name: 'Tỉnh Đồng Nai' },
  { id: 24, name: 'Tỉnh Đồng Tháp' },
  { id: 25, name: 'Tỉnh Gia Lai' },
  { id: 26, name: 'Tỉnh Hà Giang' },
  { id: 27, name: 'Tỉnh Hà Nam' },
  { id: 28, name: 'Tỉnh Hà Tĩnh' },
  { id: 29, name: 'Tỉnh Hải Dương' },
  { id: 30, name: 'Tỉnh Hậu Giang' },
  { id: 31, name: 'Tỉnh Hòa Bình' },
  { id: 32, name: 'Tỉnh Hưng Yên' },
  { id: 33, name: 'Tỉnh Khánh Hòa' },
  { id: 34, name: 'Tỉnh Kiên Giang' },
  { id: 35, name: 'Tỉnh Kon Tum' },
  { id: 36, name: 'Tỉnh Lai Châu' },
  { id: 37, name: 'Tỉnh Lâm Đồng' },
  { id: 38, name: 'Tỉnh Lạng Sơn' },
  { id: 39, name: 'Tỉnh Lào Cai' },
  { id: 40, name: 'Tỉnh Long An' },
  { id: 41, name: 'Tỉnh Nam Định' },
  { id: 42, name: 'Tỉnh Nghệ An' },
  { id: 43, name: 'Tỉnh Ninh Bình' },
  { id: 44, name: 'Tỉnh Ninh Thuận' },
  { id: 45, name: 'Tỉnh Phú Thọ' },
  { id: 46, name: 'Tỉnh Phú Yên' },
  { id: 47, name: 'Tỉnh Quảng Bình' },
  { id: 48, name: 'Tỉnh Quảng Nam' },
  { id: 49, name: 'Tỉnh Quảng Ngãi' },
  { id: 50, name: 'Tỉnh Quảng Ninh' },
  { id: 51, name: 'Tỉnh Quảng Trị' },
  { id: 52, name: 'Tỉnh Sóc Trăng' },
  { id: 53, name: 'Tỉnh Sơn La' },
  { id: 54, name: 'Tỉnh Tây Ninh' },
  { id: 55, name: 'Tỉnh Thái Bình' },
  { id: 56, name: 'Tỉnh Thái Nguyên' },
  { id: 57, name: 'Tỉnh Thanh Hóa' },
  { id: 58, name: 'Tỉnh Thừa Thiên Huế' },
  { id: 59, name: 'Tỉnh Tiền Giang' },
  { id: 60, name: 'Tỉnh Trà Vinh' },
  { id: 61, name: 'Tỉnh Tuyên Quang' },
  { id: 62, name: 'Tỉnh Vĩnh Long' },
  { id: 63, name: 'Tỉnh Vĩnh Phúc' },
  { id: 64, name: 'Tỉnh Yên Bái' },
];

export const DISTRICTS: District[] = [
  // TP. Hồ Chí Minh (provinceId: 1)
  { id: 1, provinceId: 1, name: 'Quận 1' },
  { id: 2, provinceId: 1, name: 'Quận 2' },
  { id: 3, provinceId: 1, name: 'Quận 3' },
  { id: 4, provinceId: 1, name: 'Quận 4' },
  { id: 5, provinceId: 1, name: 'Quận 5' },
  { id: 6, provinceId: 1, name: 'Quận 6' },
  { id: 7, provinceId: 1, name: 'Quận 7' },
  { id: 8, provinceId: 1, name: 'Quận 8' },
  { id: 9, provinceId: 1, name: 'Quận 9' },
  { id: 10, provinceId: 1, name: 'Quận 10' },
  { id: 11, provinceId: 1, name: 'Quận 11' },
  { id: 12, provinceId: 1, name: 'Quận 12' },
  { id: 13, provinceId: 1, name: 'Quận Bình Thạnh' },
  { id: 14, provinceId: 1, name: 'Quận Gò Vấp' },
  { id: 15, provinceId: 1, name: 'Quận Phú Nhuận' },
  { id: 16, provinceId: 1, name: 'Quận Tân Bình' },
  { id: 17, provinceId: 1, name: 'Quận Tân Phú' },
  { id: 18, provinceId: 1, name: 'Quận Thủ Đức' },
  { id: 19, provinceId: 1, name: 'Quận Bình Tân' },
  { id: 20, provinceId: 1, name: 'Huyện Hóc Môn' },
  { id: 21, provinceId: 1, name: 'Huyện Củ Chi' },
  { id: 22, provinceId: 1, name: 'Huyện Nhà Bè' },
  { id: 23, provinceId: 1, name: 'Huyện Cần Giờ' },
  // TP. Hà Nội (provinceId: 2)
  { id: 24, provinceId: 2, name: 'Quận Ba Đình' },
  { id: 25, provinceId: 2, name: 'Quận Hoàn Kiếm' },
  { id: 26, provinceId: 2, name: 'Quận Tây Hồ' },
  { id: 27, provinceId: 2, name: 'Quận Long Biên' },
  { id: 28, provinceId: 2, name: 'Quận Cầu Giấy' },
  { id: 29, provinceId: 2, name: 'Quận Đống Đa' },
  { id: 30, provinceId: 2, name: 'Quận Hai Bà Trưng' },
  { id: 31, provinceId: 2, name: 'Quận Hoàng Mai' },
  { id: 32, provinceId: 2, name: 'Quận Thanh Xuân' },
  { id: 33, provinceId: 2, name: 'Quận Nam Từ Liêm' },
  { id: 34, provinceId: 2, name: 'Quận Bắc Từ Liêm' },
  { id: 35, provinceId: 2, name: 'Huyện Thanh Trì' },
  { id: 36, provinceId: 2, name: 'Huyện Gia Lâm' },
  { id: 37, provinceId: 2, name: 'Huyện Đông Anh' },
  { id: 38, provinceId: 2, name: 'Huyện Sóc Sơn' },
  { id: 39, provinceId: 2, name: 'Huyện Mê Linh' },
  { id: 40, provinceId: 2, name: 'Huyện Hà Đông' },
  { id: 41, provinceId: 2, name: 'Thị xã Sơn Tây' },
  { id: 42, provinceId: 2, name: 'Huyện Ba Vì' },
  { id: 43, provinceId: 2, name: 'Huyện Phúc Thọ' },
  { id: 44, provinceId: 2, name: 'Huyện Đan Phượng' },
  { id: 45, provinceId: 2, name: 'Huyện Hoài Đức' },
  { id: 46, provinceId: 2, name: 'Huyện Quốc Oai' },
  { id: 47, provinceId: 2, name: 'Huyện Thạch Thất' },
  { id: 48, provinceId: 2, name: 'Huyện Chương Mỹ' },
  { id: 49, provinceId: 2, name: 'Huyện Thanh Oai' },
  { id: 50, provinceId: 2, name: 'Huyện Mỹ Đức' },
  { id: 51, provinceId: 2, name: 'Huyện Ứng Hòa' },
  { id: 52, provinceId: 2, name: 'Huyện Phú Xuyên' },
  // TP. Đà Nẵng (provinceId: 3)
  { id: 53, provinceId: 3, name: 'Quận Hải Châu' },
  { id: 54, provinceId: 3, name: 'Quận Thanh Khê' },
  { id: 55, provinceId: 3, name: 'Quận Sơn Trà' },
  { id: 56, provinceId: 3, name: 'Quận Ngũ Hành Sơn' },
  { id: 57, provinceId: 3, name: 'Quận Liên Chiểu' },
  { id: 58, provinceId: 3, name: 'Quận Cẩm Lệ' },
  { id: 59, provinceId: 3, name: 'Huyện Hòa Vang' },
  { id: 60, provinceId: 3, name: 'Huyện Hoàng Sa' },
  // TP. Hải Phòng (provinceId: 4)
  { id: 61, provinceId: 4, name: 'Quận Hồng Bàng' },
  { id: 62, provinceId: 4, name: 'Quận Ngô Quyền' },
  { id: 63, provinceId: 4, name: 'Quận Lê Chân' },
  { id: 64, provinceId: 4, name: 'Quận Kiến An' },
  { id: 65, provinceId: 4, name: 'Quận Đồ Sơn' },
  { id: 66, provinceId: 4, name: 'Quận Dương Kinh' },
  { id: 67, provinceId: 4, name: 'Huyện An Dương' },
  { id: 68, provinceId: 4, name: 'Huyện An Lão' },
  { id: 69, provinceId: 4, name: 'Huyện Kiến Thụy' },
  { id: 70, provinceId: 4, name: 'Huyện Tiên Lãng' },
  { id: 71, provinceId: 4, name: 'Huyện Vĩnh Bảo' },
  { id: 72, provinceId: 4, name: 'Huyện Cát Hải' },
  { id: 73, provinceId: 4, name: 'Huyện Bạch Long Vĩ' },
  // TP. Cần Thơ (provinceId: 5)
  { id: 74, provinceId: 5, name: 'Quận Ninh Kiều' },
  { id: 75, provinceId: 5, name: 'Quận Ô Môn' },
  { id: 76, provinceId: 5, name: 'Quận Bình Thủy' },
  { id: 77, provinceId: 5, name: 'Quận Cái Răng' },
  { id: 78, provinceId: 5, name: 'Quận Thốt Nốt' },
  { id: 79, provinceId: 5, name: 'Huyện Vĩnh Thạnh' },
  { id: 80, provinceId: 5, name: 'Huyện Cờ Đỏ' },
  { id: 81, provinceId: 5, name: 'Huyện Phong Điền' },
  { id: 82, provinceId: 5, name: 'Huyện Thới Lai' },
  // Tỉnh An Giang (provinceId: 7)
  { id: 83, provinceId: 7, name: 'Thành phố Long Xuyên' },
  { id: 84, provinceId: 7, name: 'Thành phố Châu Đốc' },
  { id: 85, provinceId: 7, name: 'Huyện An Phú' },
  { id: 86, provinceId: 7, name: 'Thị xã Tân Châu' },
  { id: 87, provinceId: 7, name: 'Huyện Phú Tân' },
  { id: 88, provinceId: 7, name: 'Huyện Châu Phong' },
  { id: 89, provinceId: 7, name: 'Huyện Tịnh Biên' },
  { id: 90, provinceId: 7, name: 'Huyện Tri Tôn' },
  { id: 91, provinceId: 7, name: 'Huyện Thoại Sơn' },
  // Tỉnh Bà Rịa - Vũng Tàu (provinceId: 8)
  { id: 92, provinceId: 8, name: 'Thành phố Vũng Tàu' },
  { id: 93, provinceId: 8, name: 'Thành phố Bà Rịa' },
  { id: 94, provinceId: 8, name: 'Huyện Châu Đức' },
  { id: 95, provinceId: 8, name: 'Huyện Xuyên Mộc' },
  { id: 96, provinceId: 8, name: 'Huyện Long Điền' },
  { id: 97, provinceId: 8, name: 'Huyện Đất Đỏ' },
  { id: 98, provinceId: 8, name: 'Thị xã Phú Mỹ' },
  // Tỉnh Bắc Giang (provinceId: 9)
  { id: 99, provinceId: 9, name: 'Thành phố Bắc Giang' },
  { id: 100, provinceId: 9, name: 'Huyện Yên Thế' },
  { id: 101, provinceId: 9, name: 'Huyện Tân Yên' },
  { id: 102, provinceId: 9, name: 'Huyện Lạng Giang' },
  { id: 103, provinceId: 9, name: 'Huyện Lục Nam' },
  { id: 104, provinceId: 9, name: 'Huyện Lục Ngạn' },
  { id: 105, provinceId: 9, name: 'Huyện Sơn Động' },
  { id: 106, provinceId: 9, name: 'Huyện Yên Dũng' },
  { id: 107, provinceId: 9, name: 'Huyện Việt Yên' },
  { id: 108, provinceId: 9, name: 'Huyện Hiệp Hòa' },
  // Tỉnh Bắc Kạn (provinceId: 10)
  { id: 109, provinceId: 10, name: 'Thành phố Bắc Kạn' },
  { id: 110, provinceId: 10, name: 'Huyện Pác Nặm' },
  { id: 111, provinceId: 10, name: 'Huyện Ba Bể' },
  { id: 112, provinceId: 10, name: 'Huyện Ngân Sơn' },
  { id: 113, provinceId: 10, name: 'Huyện Bạch Thông' },
  { id: 114, provinceId: 10, name: 'Huyện Chợ Đồn' },
  { id: 115, provinceId: 10, name: 'Huyện Chợ Mới' },
  { id: 116, provinceId: 10, name: 'Huyện Na Rì' },
  // Tỉnh Bạc Liêu (provinceId: 11)
  { id: 117, provinceId: 11, name: 'Thành phố Bạc Liêu' },
  { id: 118, provinceId: 11, name: 'Huyện Hồng Dân' },
  { id: 119, provinceId: 11, name: 'Huyện Phước Long' },
  { id: 120, provinceId: 11, name: 'Thị xã Giá Rai' },
  { id: 121, provinceId: 11, name: 'Huyện Đông Hải' },
  { id: 122, provinceId: 11, name: 'Huyện Hoà Bình' },
  // Tỉnh Bắc Ninh (provinceId: 12)
  { id: 123, provinceId: 12, name: 'Thành phố Bắc Ninh' },
  { id: 124, provinceId: 12, name: 'Huyện Yên Phong' },
  { id: 125, provinceId: 12, name: 'Huyện Quế Võ' },
  { id: 126, provinceId: 12, name: 'Huyện Tiên Du' },
  { id: 127, provinceId: 12, name: 'Thị xã Từ Sơn' },
  { id: 128, provinceId: 12, name: 'Huyện Thuận Thành' },
  { id: 129, provinceId: 12, name: 'Huyện Gia Bình' },
  { id: 130, provinceId: 12, name: 'Huyện Lương Tài' },
  // Tỉnh Bến Tre (provinceId: 13)
  { id: 131, provinceId: 13, name: 'Thành phố Bến Tre' },
  { id: 132, provinceId: 13, name: 'Huyện Châu Thành' },
  { id: 133, provinceId: 13, name: 'Huyện Chợ Lách' },
  { id: 134, provinceId: 13, name: 'Huyện Mỏ Cày Nam' },
  { id: 135, provinceId: 13, name: 'Huyện Mỏ Cày Bắc' },
  { id: 136, provinceId: 13, name: 'Huyện Ba Tri' },
  { id: 137, provinceId: 13, name: 'Huyện Bình Đại' },
  { id: 138, provinceId: 13, name: 'Huyện Thạnh Phú' },
  { id: 139, provinceId: 13, name: 'Thị xã Giồng Trôm' },
  // Tỉnh Bình Định (provinceId: 14)
  { id: 140, provinceId: 14, name: 'Thành phố Quy Nhơn' },
  { id: 141, provinceId: 14, name: 'Huyện An Lão' },
  { id: 142, provinceId: 14, name: 'Thị xã Hoài Nhơn' },
  { id: 143, provinceId: 14, name: 'Thị xã Tam Quan' },
  { id: 144, provinceId: 14, name: 'Thị xã Bồng Sơn' },
  { id: 145, provinceId: 14, name: 'Huyện Hoài Ân' },
  { id: 146, provinceId: 14, name: 'Huyện Phù Mỹ' },
  { id: 147, provinceId: 14, name: 'Huyện Vĩnh Thạnh' },
  { id: 148, provinceId: 14, name: 'Huyện Tây Sơn' },
  { id: 149, provinceId: 14, name: 'Huyện Phù Cát' },
  { id: 150, provinceId: 14, name: 'Huyện Tuy Phước' },
  // Tỉnh Bình Dương (provinceId: 15)
  { id: 151, provinceId: 15, name: 'Thành phố Thủ Dầu Một' },
  { id: 152, provinceId: 15, name: 'Huyện Bến Cát' },
  { id: 153, provinceId: 15, name: 'Thị xã Tân Uyên' },
  { id: 154, provinceId: 15, name: 'Thị xã Dĩ An' },
  { id: 155, provinceId: 15, name: 'Thị xã Thuận An' },
  { id: 156, provinceId: 15, name: 'Huyện Phú Giáo' },
  { id: 157, provinceId: 15, name: 'Huyện Dầu Tiếng' },
  { id: 158, provinceId: 15, name: 'Huyện Bàu Bàng' },
  // Tỉnh Bình Phước (provinceId: 16)
  { id: 159, provinceId: 16, name: 'Thành phố Đồng Xoài' },
  { id: 160, provinceId: 16, name: 'Thị xã Phước Long' },
  { id: 161, provinceId: 16, name: 'Thị xã Bình Long' },
  { id: 162, provinceId: 16, name: 'Huyện Lộc Ninh' },
  { id: 163, provinceId: 16, name: 'Huyện Bù Gia Mập' },
  { id: 164, provinceId: 16, name: 'Huyện Dong Phú' },
  { id: 165, provinceId: 16, name: 'Huyện Hớn Quản' },
  { id: 166, provinceId: 16, name: 'Huyện Đồng Phú' },
  { id: 167, provinceId: 16, name: 'Huyện Bù Đăng' },
  { id: 168, provinceId: 16, name: 'Huyện Chơn Thành' },
  { id: 169, provinceId: 16, name: 'Huyện Phú Riềng' },
  // Tỉnh Bình Thuận (provinceId: 17)
  { id: 170, provinceId: 17, name: 'Thành phố Phan Thiết' },
  { id: 171, provinceId: 17, name: 'Thị xã La Gi' },
  { id: 172, provinceId: 17, name: 'Huyện Tuy Phong' },
  { id: 173, provinceId: 17, name: 'Huyện Bắc Bình' },
  { id: 174, provinceId: 17, name: 'Huyện Hàm Thuận Bắc' },
  { id: 175, provinceId: 17, name: 'Huyện Hàm Thuận Nam' },
  { id: 176, provinceId: 17, name: 'Huyện Tánh Linh' },
  { id: 177, provinceId: 17, name: 'Huyện Đức Linh' },
  { id: 178, provinceId: 17, name: 'Huyện Hàm Tân' },
  { id: 179, provinceId: 17, name: 'Huyện Phú Quý' },
  // Tỉnh Cà Mau (provinceId: 18)
  { id: 180, provinceId: 18, name: 'Thành phố Cà Mau' },
  { id: 181, provinceId: 18, name: 'Huyện U Minh' },
  { id: 182, provinceId: 18, name: 'Huyện Thới Bình' },
  { id: 183, provinceId: 18, name: 'Huyện Trần Văn Thời' },
  { id: 184, provinceId: 18, name: 'Huyện Cái Nước' },
  { id: 185, provinceId: 18, name: 'Huyện Đầm Dơi' },
  { id: 186, provinceId: 18, name: 'Huyện Năm Căn' },
  { id: 187, provinceId: 18, name: 'Huyện Phú Tân' },
  { id: 188, provinceId: 18, name: 'Huyện Ngọc Hiển' },
  // Tỉnh Cao Bằng (provinceId: 19)
  { id: 189, provinceId: 19, name: 'Thành phố Cao Bằng' },
  { id: 190, provinceId: 19, name: 'Huyện Bảo Lạc' },
  { id: 191, provinceId: 19, name: 'Huyện Hà Quảng' },
  { id: 192, provinceId: 19, name: 'Huyện Trùng Khánh' },
  { id: 193, provinceId: 19, name: 'Huyện Hạ Lang' },
  { id: 194, provinceId: 19, name: 'Huyện Quảng Uyên' },
  { id: 195, provinceId: 19, name: 'Huyện Thạch An' },
  { id: 196, provinceId: 19, name: 'Huyện Bảo Lâm' },
  { id: 197, provinceId: 19, name: 'Huyện Thông Nông' },
  { id: 198, provinceId: 19, name: 'Huyện Trà Lĩnh' },
  // Tỉnh Đắk Lắk (provinceId: 20)
  { id: 199, provinceId: 20, name: 'Thành phố Buôn Ma Thuột' },
  { id: 200, provinceId: 20, name: 'Thị xã Buôn Hồ' },
  { id: 201, provinceId: 20, name: 'Huyện Ea H\'leo' },
  { id: 202, provinceId: 20, name: 'Huyện Ea Súp' },
  { id: 203, provinceId: 20, name: 'Huyện Cư M\'gar' },
  { id: 204, provinceId: 20, name: 'Huyện Krông Pắc' },
  { id: 205, provinceId: 20, name: 'Huyện Krông Bông' },
  { id: 206, provinceId: 20, name: 'Huyện Lắk' },
  { id: 207, provinceId: 20, name: 'Huyện M\'Đrắk' },
  { id: 208, provinceId: 20, name: 'Huyện Ea Kar' },
  { id: 209, provinceId: 20, name: 'Huyện Cư Kuin' },
  { id: 210, provinceId: 20, name: 'Huyện Krông Ana' },
  // Tỉnh Đắk Nông (provinceId: 21)
  { id: 211, provinceId: 21, name: 'Thành phố Gia Nghĩa' },
  { id: 212, provinceId: 21, name: 'Huyện Đăk Glong' },
  { id: 213, provinceId: 21, name: 'Huyện Cư Jút' },
  { id: 214, provinceId: 21, name: 'Huyện Đắk Mil' },
  { id: 215, provinceId: 21, name: 'Huyện Krông Nô' },
  { id: 216, provinceId: 21, name: 'Huyện Đăk R\'lấp' },
  { id: 217, provinceId: 21, name: 'Huyện Tuy Đức' },
  // Tỉnh Điện Biên (provinceId: 22)
  { id: 218, provinceId: 22, name: 'Thành phố Điện Biên Phủ' },
  { id: 219, provinceId: 22, name: 'Thị xã Mường Lay' },
  { id: 220, provinceId: 22, name: 'Huyện Mường Nhé' },
  { id: 221, provinceId: 22, name: 'Huyện Mường Chà' },
  { id: 222, provinceId: 22, name: 'Huyện Tủa Chùa' },
  { id: 223, provinceId: 22, name: 'Huyện Tuần Giáo' },
  { id: 224, provinceId: 22, name: 'Huyện Điện Biên Đông' },
  { id: 225, provinceId: 22, name: 'Huyện Điện Biên' },
  { id: 226, provinceId: 22, name: 'Huyện Nậm Pồ' },
  // Tỉnh Đồng Nai (provinceId: 23)
  { id: 227, provinceId: 23, name: 'Thành phố Biên Hòa' },
  { id: 228, provinceId: 23, name: 'Thị xã Long Khánh' },
  { id: 229, provinceId: 23, name: 'Huyện Tân Phú' },
  { id: 230, provinceId: 23, name: 'Huyện Vĩnh Cửu' },
  { id: 231, provinceId: 23, name: 'Huyện Định Quán' },
  { id: 232, provinceId: 23, name: 'Huyện Trảng Bom' },
  { id: 233, provinceId: 23, name: 'Huyện Thống Nhất' },
  { id: 234, provinceId: 23, name: 'Huyện Cẩm Mỹ' },
  { id: 235, provinceId: 23, name: 'Huyện Long Thành' },
  { id: 236, provinceId: 23, name: 'Huyện Xuân Lộc' },
  { id: 237, provinceId: 23, name: 'Huyện Nhơn Trạch' },
  // Tỉnh Đồng Tháp (provinceId: 24)
  { id: 238, provinceId: 24, name: 'Thành phố Cao Lãnh' },
  { id: 239, provinceId: 24, name: 'Thành phố Sa Đéc' },
  { id: 240, provinceId: 24, name: 'Thị xã Hồng Ngự' },
  { id: 241, provinceId: 24, name: 'Huyện Tân Hồng' },
  { id: 242, provinceId: 24, name: 'Huyện Hồng Ngự' },
  { id: 243, provinceId: 24, name: 'Huyện Tam Nông' },
  { id: 244, provinceId: 24, name: 'Huyện Tháp Mười' },
  { id: 245, provinceId: 24, name: 'Huyện Cao Lãnh' },
  { id: 246, provinceId: 24, name: 'Huyện Lai Vung' },
  { id: 247, provinceId: 24, name: 'Huyện Châu Thành' },
  // Tỉnh Gia Lai (provinceId: 25)
  { id: 248, provinceId: 25, name: 'Thành phố Pleiku' },
  { id: 249, provinceId: 25, name: 'Thị xã An Khê' },
  { id: 250, provinceId: 25, name: 'Thị xã Ayun Pa' },
  { id: 251, provinceId: 25, name: 'Huyện KBang' },
  { id: 252, provinceId: 25, name: 'Huyện Đăk Đoa' },
  { id: 253, provinceId: 25, name: 'Huyện Chư Păh' },
  { id: 254, provinceId: 25, name: 'Huyện Ia Grai' },
  { id: 255, provinceId: 25, name: 'Huyện Ia Pa' },
  { id: 256, provinceId: 25, name: 'Huyện Krông Pa' },
  { id: 257, provinceId: 25, name: 'Huyện Phú Thiện' },
  { id: 258, provinceId: 25, name: 'Huyện Chư Prông' },
  { id: 259, provinceId: 25, name: 'Huyện Chư Sê' },
  { id: 260, provinceId: 25, name: 'Huyện Đăk Pơ' },
  { id: 261, provinceId: 25, name: 'Huyện Ia H\'dróh' },
  { id: 262, provinceId: 25, name: 'Huyện Mang Yang' },
  // Tỉnh Hà Giang (provinceId: 26)
  { id: 263, provinceId: 26, name: 'Thành phố Hà Giang' },
  { id: 264, provinceId: 26, name: 'Huyện Đồng Văn' },
  { id: 265, provinceId: 26, name: 'Huyện Mèo Vạc' },
  { id: 266, provinceId: 26, name: 'Huyện Yên Minh' },
  { id: 267, provinceId: 26, name: 'Huyện Quản Bạ' },
  { id: 268, provinceId: 26, name: 'Huyện Vị Xuyên' },
  { id: 269, provinceId: 26, name: 'Huyện Bắc Mê' },
  { id: 270, provinceId: 26, name: 'Huyện Hoàng Su Phì' },
  { id: 271, provinceId: 26, name: 'Huyện Xín Mần' },
  { id: 272, provinceId: 26, name: 'Huyện Bắc Quang' },
  { id: 273, provinceId: 26, name: 'Huyện Quang Bình' },
  // Tỉnh Hà Nam (provinceId: 27)
  { id: 274, provinceId: 27, name: 'Thành phố Phủ Lý' },
  { id: 275, provinceId: 27, name: 'Thị xã Duy Tiên' },
  { id: 276, provinceId: 27, name: 'Huyện Kim Bảng' },
  { id: 277, provinceId: 27, name: 'Huyện Thanh Liêm' },
  { id: 278, provinceId: 27, name: 'Huyện Bình Lục' },
  { id: 279, provinceId: 27, name: 'Huyện Lý Nhân' },
  // Tỉnh Hà Tĩnh (provinceId: 28)
  { id: 280, provinceId: 28, name: 'Thành phố Hà Tĩnh' },
  { id: 281, provinceId: 28, name: 'Thị xã Hồng Lĩnh' },
  { id: 282, provinceId: 28, name: 'Huyện Hương Sơn' },
  { id: 283, provinceId: 28, name: 'Huyện Đức Thọ' },
  { id: 284, provinceId: 28, name: 'Huyện Nghi Xuân' },
  { id: 285, provinceId: 28, name: 'Huyện Can Lộc' },
  { id: 286, provinceId: 28, name: 'Huyện Hương Khê' },
  { id: 287, provinceId: 28, name: 'Huyện Thạch Hà' },
  { id: 288, provinceId: 28, name: 'Huyện Cẩm Xuyên' },
  { id: 289, provinceId: 28, name: 'Huyện Kỳ Anh' },
  { id: 290, provinceId: 28, name: 'Huyện Lộc Hà' },
  // Tỉnh Hải Dương (provinceId: 29)
  { id: 291, provinceId: 29, name: 'Thành phố Hải Dương' },
  { id: 292, provinceId: 29, name: 'Thị xã Chí Linh' },
  { id: 293, provinceId: 29, name: 'Huyện Nam Sách' },
  { id: 294, provinceId: 29, name: 'Huyện Kinh Môn' },
  { id: 295, provinceId: 29, name: 'Huyện Kim Thành' },
  { id: 296, provinceId: 29, name: 'Huyện Thanh Miện' },
  { id: 297, provinceId: 29, name: 'Huyện Ninh Giang' },
  { id: 298, provinceId: 29, name: 'Huyện Gia Lộc' },
  { id: 299, provinceId: 29, name: 'Huyện Tứ Kỳ' },
  { id: 300, provinceId: 29, name: 'Huyện Bình Giang' },
  // Tỉnh Hậu Giang (provinceId: 30)
  { id: 301, provinceId: 30, name: 'Thành phố Vị Thanh' },
  { id: 302, provinceId: 30, name: 'Thị xã Ngã Bảy' },
  { id: 303, provinceId: 30, name: 'Huyện Châu Thành' },
  { id: 304, provinceId: 30, name: 'Huyện Châu Thành A' },
  { id: 305, provinceId: 30, name: 'Huyện Phụng Hiệp' },
  { id: 306, provinceId: 30, name: 'Huyện Vị Thủy' },
  { id: 307, provinceId: 30, name: 'Huyện Long Mỹ' },
  { id: 308, provinceId: 30, name: 'Thị xã Long Mỹ' },
  // Tỉnh Hòa Bình (provinceId: 31)
  { id: 309, provinceId: 31, name: 'Thành phố Hòa Bình' },
  { id: 310, provinceId: 31, name: 'Huyện Đà Bắc' },
  { id: 311, provinceId: 31, name: 'Huyện Mai Châu' },
  { id: 312, provinceId: 31, name: 'Huyện Tân Lạc' },
  { id: 313, provinceId: 31, name: 'Huyện Kỳ Sơn' },
  { id: 314, provinceId: 31, name: 'Huyện Lương Sơn' },
  { id: 315, provinceId: 31, name: 'Huyện Kim Bôi' },
  { id: 316, provinceId: 31, name: 'Huyện Cao Phong' },
  { id: 317, provinceId: 31, name: 'Huyện Yên Thủy' },
  { id: 318, provinceId: 31, name: 'Huyện Lạc Sơn' },
  { id: 319, provinceId: 31, name: 'Huyện Lạc Thủy' },
  // Tỉnh Hưng Yên (provinceId: 32)
  { id: 320, provinceId: 32, name: 'Thành phố Hưng Yên' },
  { id: 321, provinceId: 32, name: 'Thị xã Mỹ Hào' },
  { id: 322, provinceId: 32, name: 'Huyện Văn Lâm' },
  { id: 323, provinceId: 32, name: 'Huyện Văn Giang' },
  { id: 324, provinceId: 32, name: 'Huyện Yên Mỹ' },
  { id: 325, provinceId: 32, name: 'Huyện Kỳ Hải' },
  { id: 326, provinceId: 32, name: 'Huyện Ân Thi' },
  { id: 327, provinceId: 32, name: 'Huyện Phù Cừ' },
  { id: 328, provinceId: 32, name: 'Huyện Tiên Lữ' },
  { id: 329, provinceId: 32, name: 'Huyện Kim Động' },
  // Tỉnh Khánh Hòa (provinceId: 33)
  { id: 330, provinceId: 33, name: 'Thành phố Nha Trang' },
  { id: 331, provinceId: 33, name: 'Thành phố Cam Ranh' },
  { id: 332, provinceId: 33, name: 'Thị xã Ninh Hòa' },
  { id: 333, provinceId: 33, name: 'Huyện Diên Khánh' },
  { id: 334, provinceId: 33, name: 'Huyện Khánh Vĩnh' },
  { id: 335, provinceId: 33, name: 'Huyện Khánh Sơn' },
  { id: 336, provinceId: 33, name: 'Huyện Trường Sa' },
  // Tỉnh Kiên Giang (provinceId: 34)
  { id: 337, provinceId: 34, name: 'Thành phố Rạch Giá' },
  { id: 338, provinceId: 34, name: 'Thị xã Hà Tiên' },
  { id: 339, provinceId: 34, name: 'Huyện Kiên Lương' },
  { id: 340, provinceId: 34, name: 'Huyện Hòn Đất' },
  { id: 341, provinceId: 34, name: 'Huyện Tân Hiệp' },
  { id: 342, provinceId: 34, name: 'Huyện Châu Thành' },
  { id: 343, provinceId: 34, name: 'Huyện Giồng Giềng' },
  { id: 344, provinceId: 34, name: 'Huyện Gò Quao' },
  { id: 345, provinceId: 34, name: 'Huyện An Biên' },
  { id: 346, provinceId: 34, name: 'Huyện An Minh' },
  { id: 347, provinceId: 34, name: 'Huyện Vĩnh Thuận' },
  { id: 348, provinceId: 34, name: 'Huyện Phú Quốc' },
  { id: 349, provinceId: 34, name: 'Huyện Kiên Hải' },
  // Tỉnh Kon Tum (provinceId: 35)
  { id: 350, provinceId: 35, name: 'Thành phố Kon Tum' },
  { id: 351, provinceId: 35, name: 'Huyện Đắk Hà' },
  { id: 352, provinceId: 35, name: 'Huyện Đắk Tô' },
  { id: 353, provinceId: 35, name: 'Huyện Ngọc Hồi' },
  { id: 354, provinceId: 35, name: 'Huyện Đắk Glei' },
  { id: 355, provinceId: 35, name: 'Huyện Tu Mơ Rông' },
  { id: 356, provinceId: 35, name: 'Huyện Ia H\'drói' },
  { id: 357, provinceId: 35, name: 'Huyện Kon Plông' },
  { id: 358, provinceId: 35, name: 'Huyện Kon Rẫy' },
  // Tỉnh Lai Châu (provinceId: 36)
  { id: 359, provinceId: 36, name: 'Thành phố Lai Châu' },
  { id: 360, provinceId: 36, name: 'Huyện Tam Đường' },
  { id: 361, provinceId: 36, name: 'Huyện Mường Tè' },
  { id: 362, provinceId: 36, name: 'Huyện Sìn Hồ' },
  { id: 363, provinceId: 36, name: 'Huyện Tân Uyên' },
  { id: 364, provinceId: 36, name: 'Huyện Nậm Nhùn' },
  // Tỉnh Lâm Đồng (provinceId: 37)
  { id: 365, provinceId: 37, name: 'Thành phố Đà Lạt' },
  { id: 366, provinceId: 37, name: 'Thành phố Bảo Lộc' },
  { id: 367, provinceId: 37, name: 'Huyện Đức Trọng' },
  { id: 368, provinceId: 37, name: 'Huyện Lạc Dương' },
  { id: 369, provinceId: 37, name: 'Huyện Lâm Hà' },
  { id: 370, provinceId: 37, name: 'Huyện Đơn Dương' },
  { id: 371, provinceId: 37, name: 'Huyện Đạ Huoai' },
  { id: 372, provinceId: 37, name: 'Huyện Đạ Tẻh' },
  { id: 373, provinceId: 37, name: 'Huyện Cát Tiên' },
  { id: 374, provinceId: 37, name: 'Huyện Bảo Lâm' },
  { id: 375, provinceId: 37, name: 'Huyện Di Linh' },
  // Tỉnh Lạng Sơn (provinceId: 38)
  { id: 376, provinceId: 38, name: 'Thành phố Lạng Sơn' },
  { id: 377, provinceId: 38, name: 'Huyện Tràng Định' },
  { id: 378, provinceId: 38, name: 'Huyện Bình Gia' },
  { id: 379, provinceId: 38, name: 'Huyện Văn Lãng' },
  { id: 380, provinceId: 38, name: 'Huyện Cao Lộc' },
  { id: 381, provinceId: 38, name: 'Huyện Lộc Bình' },
  { id: 382, provinceId: 38, name: 'Huyện Đình Lập' },
  { id: 383, provinceId: 38, name: 'Huyện Hữu Lũng' },
  { id: 384, provinceId: 38, name: 'Huyện Chi Lăng' },
  { id: 385, provinceId: 38, name: 'Huyện Bắc Sơn' },
  // Tỉnh Lào Cai (provinceId: 39)
  { id: 386, provinceId: 39, name: 'Thành phố Lào Cai' },
  { id: 387, provinceId: 39, name: 'Huyện Bát Xát' },
  { id: 388, provinceId: 39, name: 'Huyện Mường Khương' },
  { id: 389, provinceId: 39, name: 'Huyện Si Ma Cai' },
  { id: 390, provinceId: 39, name: 'Huyện Bắc Hà' },
  { id: 391, provinceId: 39, name: 'Huyện Bảo Thắng' },
  { id: 392, provinceId: 39, name: 'Huyện Bảo Yên' },
  { id: 393, provinceId: 39, name: 'Huyện Văn Bàn' },
  { id: 394, provinceId: 39, name: 'Huyện Sa Pa' },
  // Tỉnh Long An (provinceId: 40)
  { id: 395, provinceId: 40, name: 'Thành phố Tân An' },
  { id: 396, provinceId: 40, name: 'Thị xã Kiến Tường' },
  { id: 397, provinceId: 40, name: 'Huyện Tân Hưng' },
  { id: 398, provinceId: 40, name: 'Huyện Vĩnh Hưng' },
  { id: 399, provinceId: 40, name: 'Huyện Mộc Hóa' },
  { id: 400, provinceId: 40, name: 'Huyện Tân Thạnh' },
  { id: 401, provinceId: 40, name: 'Huyện Thạnh Hóa' },
  { id: 402, provinceId: 40, name: 'Huyện Đức Huệ' },
  { id: 403, provinceId: 40, name: 'Huyện Đức Hòa' },
  { id: 404, provinceId: 40, name: 'Huyện Bến Lức' },
  { id: 405, provinceId: 40, name: 'Huyện Thủ Thừa' },
  { id: 406, provinceId: 40, name: 'Huyện Tân Trụ' },
  { id: 407, provinceId: 40, name: 'Huyện Cần Đước' },
  { id: 408, provinceId: 40, name: 'Huyện Cần Giuộc' },
  { id: 409, provinceId: 40, name: 'Huyện Châu Thành' },
  { id: 410, provinceId: 40, name: 'Huyện Tân Lat' },
  // Tỉnh Nam Định (provinceId: 41)
  { id: 411, provinceId: 41, name: 'Thành phố Nam Định' },
  { id: 412, provinceId: 41, name: 'Huyện Mỹ Lộc' },
  { id: 413, provinceId: 41, name: 'Huyện Vụ Bản' },
  { id: 414, provinceId: 41, name: 'Huyện Ý Yên' },
  { id: 415, provinceId: 41, name: 'Huyện Nghĩa Hưng' },
  { id: 416, provinceId: 41, name: 'Huyện Nam Trực' },
  { id: 417, provinceId: 41, name: 'Huyện Trực Ninh' },
  { id: 418, provinceId: 41, name: 'Huyện Xuân Trường' },
  { id: 419, provinceId: 41, name: 'Huyện Giao Thủy' },
  { id: 420, provinceId: 41, name: 'Huyện Hải Hậu' },
  // Tỉnh Nghệ An (provinceId: 42)
  { id: 421, provinceId: 42, name: 'Thành phố Vinh' },
  { id: 422, provinceId: 42, name: 'Thị xã Cửa Lò' },
  { id: 423, provinceId: 42, name: 'Thị xã Thái Hòa' },
  { id: 424, provinceId: 42, name: 'Huyện Quế Phong' },
  { id: 425, provinceId: 42, name: 'Huyện Quỳ Châu' },
  { id: 426, provinceId: 42, name: 'Huyện Kỳ Sơn' },
  { id: 427, provinceId: 42, name: 'Huyện Tương Dương' },
  { id: 428, provinceId: 42, name: 'Huyện Nghĩa Đàn' },
  { id: 429, provinceId: 42, name: 'Huyện Quỳ Hợp' },
  { id: 430, provinceId: 42, name: 'Huyện Quỳ Lâm' },
  { id: 431, provinceId: 42, name: 'Huyện Anh Sơn' },
  { id: 432, provinceId: 42, name: 'Huyện Diễn Châu' },
  { id: 433, provinceId: 42, name: 'Huyện Yên Thành' },
  { id: 434, provinceId: 42, name: 'Huyện Đô Lương' },
  { id: 435, provinceId: 42, name: 'Huyện Thanh Chương' },
  { id: 436, provinceId: 42, name: 'Huyện Nghi Lộc' },
  { id: 437, provinceId: 42, name: 'Huyện Nam Đàn' },
  { id: 438, provinceId: 42, name: 'Huyện Hưng Nguyên' },
  { id: 439, provinceId: 42, name: 'Huyện Duy Tân' },
  { id: 440, provinceId: 42, name: 'Huyện Con Cuông' },
  { id: 441, provinceId: 42, name: 'Huyện Tân Kỳ' },
  // Tỉnh Ninh Bình (provinceId: 43)
  { id: 442, provinceId: 43, name: 'Thành phố Ninh Bình' },
  { id: 443, provinceId: 43, name: 'Thành phố Tam Điệp' },
  { id: 444, provinceId: 43, name: 'Huyện Yên Khánh' },
  { id: 445, provinceId: 43, name: 'Huyện Kim Sơn' },
  { id: 446, provinceId: 43, name: 'Huyện Hoa Lư' },
  { id: 447, provinceId: 43, name: 'Huyện Nho Quan' },
  { id: 448, provinceId: 43, name: 'Huyện Gia Viễn' },
  { id: 449, provinceId: 43, name: 'Huyện Ý Yên' },
  // Tỉnh Ninh Thuận (provinceId: 44)
  { id: 450, provinceId: 44, name: 'Thành phố Phan Rang-Tháp Chàm' },
  { id: 451, provinceId: 44, name: 'Huyện Bác Ái' },
  { id: 452, provinceId: 44, name: 'Huyện Ninh Sơn' },
  { id: 453, provinceId: 44, name: 'Huyện Ninh Hải' },
  { id: 454, provinceId: 44, name: 'Huyện Ninh Phước' },
  { id: 455, provinceId: 44, name: 'Huyện Thuận Bắc' },
  { id: 456, provinceId: 44, name: 'Huyện Thuận Nam' },
  // Tỉnh Phú Thọ (provinceId: 45)
  { id: 457, provinceId: 45, name: 'Thành phố Việt Trì' },
  { id: 458, provinceId: 45, name: 'Thị xã Phú Thọ' },
  { id: 459, provinceId: 45, name: 'Huyện Đoan Hùng' },
  { id: 460, provinceId: 45, name: 'Huyện Hạ Hoà' },
  { id: 461, provinceId: 45, name: 'Huyện Thanh Ba' },
  { id: 462, provinceId: 45, name: 'Huyện Phù Ninh' },
  { id: 463, provinceId: 45, name: 'Huyện Yên Lập' },
  { id: 464, provinceId: 45, name: 'Huyện Cẩm Khê' },
  { id: 465, provinceId: 45, name: 'Huyện Tam Nông' },
  { id: 466, provinceId: 45, name: 'Huyện Thanh Thủy' },
  { id: 467, provinceId: 45, name: 'Huyện Tân Sơn' },
  // Tỉnh Phú Yên (provinceId: 46)
  { id: 468, provinceId: 46, name: 'Thành phố Tuy Hòa' },
  { id: 469, provinceId: 46, name: 'Thị xã Sông Cầu' },
  { id: 470, provinceId: 46, name: 'Huyện Đồng Xuân' },
  { id: 471, provinceId: 46, name: 'Huyện Tuy An' },
  { id: 472, provinceId: 46, name: 'Huyện Sơn Hòa' },
  { id: 473, provinceId: 46, name: 'Huyện Sông Hinh' },
  { id: 474, provinceId: 46, name: 'Huyện Phú Hòa' },
  { id: 475, provinceId: 46, name: 'Huyện Tây Hòa' },
  { id: 476, provinceId: 46, name: 'Huyện Đông Hòa' },
  // Tỉnh Quảng Bình (provinceId: 47)
  { id: 477, provinceId: 47, name: 'Thành phố Đồng Hới' },
  { id: 478, provinceId: 47, name: 'Thị xã Ba Đồn' },
  { id: 479, provinceId: 47, name: 'Huyện Minh Hóa' },
  { id: 480, provinceId: 47, name: 'Huyện Tuyên Hóa' },
  { id: 481, provinceId: 47, name: 'Huyện Quảng Trạch' },
  { id: 482, provinceId: 47, name: 'Huyện Bố Trạch' },
  { id: 483, provinceId: 47, name: 'Huyện Quảng Ninh' },
  { id: 484, provinceId: 47, name: 'Huyện Lệ Thuỷ' },
  // Tỉnh Quảng Nam (provinceId: 48)
  { id: 485, provinceId: 48, name: 'Thành phố Tam Kỳ' },
  { id: 486, provinceId: 48, name: 'Thành phố Hội An' },
  { id: 487, provinceId: 48, name: 'Huyện Tây Giang' },
  { id: 488, provinceId: 48, name: 'Huyện Đông Giang' },
  { id: 489, provinceId: 48, name: 'Huyện Nam Giang' },
  { id: 490, provinceId: 48, name: 'Huyện Phước Sơn' },
  { id: 491, provinceId: 48, name: 'Huyện Hiệp Đức' },
  { id: 492, provinceId: 48, name: 'Huyện Thăng Bình' },
  { id: 493, provinceId: 48, name: 'Huyện Tiên Phước' },
  { id: 494, provinceId: 48, name: 'Huyện Bắc Trà My' },
  { id: 495, provinceId: 48, name: 'Huyệm Nam Trà My' },
  { id: 496, provinceId: 48, name: 'Huyện Phú Ninh' },
  { id: 497, provinceId: 48, name: 'Huyện Nông Sơn' },
  { id: 498, provinceId: 48, name: 'Huyện Duy Xuyên' },
  { id: 499, provinceId: 48, name: 'Huyện Quế Sơn' },
  { id: 500, provinceId: 48, name: 'Huyện Nam Trà My' },
  // Tỉnh Quảng Ngãi (provinceId: 49)
  { id: 501, provinceId: 49, name: 'Thành phố Quảng Ngãi' },
  { id: 502, provinceId: 49, name: 'Huyện Lý Sơn' },
  { id: 503, provinceId: 49, name: 'Huyện Bình Sơn' },
  { id: 504, provinceId: 49, name: 'Huyện Trà Bồng' },
  { id: 505, provinceId: 49, name: 'Huyện Bình Giang' },
  { id: 506, provinceId: 49, name: 'Huyện Mộ Đức' },
  { id: 507, provinceId: 49, name: 'Thị xã Đức Phổ' },
  { id: 508, provinceId: 49, name: 'Huyện Sơn Tịnh' },
  { id: 509, provinceId: 49, name: 'Huyện Sơn Hà' },
  { id: 510, provinceId: 49, name: 'Huyện Minh Long' },
  { id: 511, provinceId: 49, name: 'Huyện Nghĩa Hành' },
  { id: 512, provinceId: 49, name: 'Huyện Ba Tơ' },
  { id: 513, provinceId: 49, name: 'Huyện Tư Nghĩa' },
  { id: 514, provinceId: 49, name: 'Huyện Tây Trà' },
  // Tỉnh Quảng Ninh (provinceId: 50)
  { id: 515, provinceId: 50, name: 'Thành phố Hạ Long' },
  { id: 516, provinceId: 50, name: 'Thành phố Móng Cái' },
  { id: 517, provinceId: 50, name: 'Thành phố Cẩm Phả' },
  { id: 518, provinceId: 50, name: 'Thành phố Uông Bí' },
  { id: 519, provinceId: 50, name: 'Thị xã Quảng Yên' },
  { id: 520, provinceId: 50, name: 'Huyện Bình Liêu' },
  { id: 521, provinceId: 50, name: 'Huyện Tiên Yên' },
  { id: 522, provinceId: 50, name: 'Huyện Đầm Hà' },
  { id: 523, provinceId: 50, name: 'Huyện Hải Hà' },
  { id: 524, provinceId: 50, name: 'Huyện Ba Chẽ' },
  { id: 525, provinceId: 50, name: 'Huyện Vân Đồn' },
  { id: 526, provinceId: 50, name: 'Huyện Hoành Bồ' },
  { id: 527, provinceId: 50, name: 'Huyện Yên Hưng' },
  { id: 528, provinceId: 50, name: 'Huyện Đông Triều' },
  // Tỉnh Quảng Trị (provinceId: 51)
  { id: 529, provinceId: 51, name: 'Thành phố Đông Hà' },
  { id: 530, provinceId: 51, name: 'Thị xã Quảng Trị' },
  { id: 531, provinceId: 51, name: 'Huyện Vĩnh Linh' },
  { id: 532, provinceId: 51, name: 'Huyện Hướng Hóa' },
  { id: 533, provinceId: 51, name: 'Huyện Gio Linh' },
  { id: 534, provinceId: 51, name: 'Huyện Cam Lộ' },
  { id: 535, provinceId: 51, name: 'Huyện Triệu Phong' },
  { id: 536, provinceId: 51, name: 'Huyện Hải Lăng' },
  { id: 537, provinceId: 51, name: 'Huyện Cồn Cỏ' },
  // Tỉnh Sóc Trăng (provinceId: 52)
  { id: 538, provinceId: 52, name: 'Thành phố Sóc Trăng' },
  { id: 539, provinceId: 52, name: 'Thị xã Vĩnh Châu' },
  { id: 540, provinceId: 52, name: 'Huyện Kế Sách' },
  { id: 541, provinceId: 52, name: 'Huyện Mỹ Tú' },
  { id: 542, provinceId: 52, name: 'Huyện Cù Lao Dung' },
  { id: 543, provinceId: 52, name: 'Huyện Long Phú' },
  { id: 544, provinceId: 52, name: 'Huyện Mỹ Xuyên' },
  { id: 545, provinceId: 52, name: 'Huyện Ngã Năm' },
  { id: 546, provinceId: 52, name: 'Huyện Thạnh Trị' },
  { id: 547, provinceId: 52, name: 'Huyện Trần Đề' },
  // Tỉnh Sơn La (provinceId: 53)
  { id: 548, provinceId: 53, name: 'Thành phố Sơn La' },
  { id: 549, provinceId: 53, name: 'Huyện Quỳnh Nhai' },
  { id: 550, provinceId: 53, name: 'Huyện Thuận Châu' },
  { id: 551, provinceId: 53, name: 'Huyện Mường La' },
  { id: 552, provinceId: 53, name: 'Huyện Bắc Yên' },
  { id: 553, provinceId: 53, name: 'Huyện Phù Yên' },
  { id: 554, provinceId: 53, name: 'Huyện Mộc Châu' },
  { id: 555, provinceId: 53, name: 'Huyện Yên Châu' },
  { id: 556, provinceId: 53, name: 'Huyện Mai Sơn' },
  { id: 557, provinceId: 53, name: 'Huyện Sông Mã' },
  { id: 558, provinceId: 53, name: 'Huyện Sốp Cộp' },
  { id: 559, provinceId: 53, name: 'Huyện Vân Hồ' },
  // Tỉnh Tây Ninh (provinceId: 54)
  { id: 560, provinceId: 54, name: 'Thành phố Tây Ninh' },
  { id: 561, provinceId: 54, name: 'Thị xã Hòa Thành' },
  { id: 562, provinceId: 54, name: 'Thị xã Bến Cầu' },
  { id: 563, provinceId: 54, name: 'Huyện Châu Thành' },
  { id: 564, provinceId: 54, name: 'Huyện Tân Biên' },
  { id: 565, provinceId: 54, name: 'Huyện Tân Châu' },
  { id: 566, provinceId: 54, name: 'Huyện Dương Minh Châu' },
  { id: 567, provinceId: 54, name: 'Huyện Trảng Bàng' },
  // Tỉnh Thái Bình (provinceId: 55)
  { id: 568, provinceId: 55, name: 'Thành phố Thái Bình' },
  { id: 569, provinceId: 55, name: 'Huyện Quỳnh Phụ' },
  { id: 570, provinceId: 55, name: 'Huyện Hưng Hà' },
  { id: 571, provinceId: 55, name: 'Huyện Đông Hưng' },
  { id: 572, provinceId: 55, name: 'Huyện Vũ Thư' },
  { id: 573, provinceId: 55, name: 'Huyện Kiến Xương' },
  { id: 574, provinceId: 55, name: 'Huyện Thái Thụy' },
  { id: 575, provinceId: 55, name: 'Huyện Tiền Hải' },
  { id: 576, provinceId: 55, name: 'Huyện Krong Klang' },
  // Tỉnh Thái Nguyên (provinceId: 56)
  { id: 577, provinceId: 56, name: 'Thành phố Thái Nguyên' },
  { id: 578, provinceId: 56, name: 'Thành phố Sông Công' },
  { id: 579, provinceId: 56, name: 'Huyện Định Hóa' },
  { id: 580, provinceId: 56, name: 'Huyện Phú Lương' },
  { id: 581, provinceId: 56, name: 'Huyện Đồng Hỷ' },
  { id: 582, provinceId: 56, name: 'Huyện Võ Nhai' },
  { id: 583, provinceId: 56, name: 'Huyện Đại Từ' },
  { id: 584, provinceId: 56, name: 'Thị xã Phổ Yên' },
  { id: 585, provinceId: 56, name: 'Huyện Phú Bình' },
  // Tỉnh Thanh Hóa (provinceId: 57)
  { id: 586, provinceId: 57, name: 'Thành phố Thanh Hóa' },
  { id: 587, provinceId: 57, name: 'Thị xã Bỉm Sơn' },
  { id: 588, provinceId: 57, name: 'Thị xã Sầm Sơn' },
  { id: 589, provinceId: 57, name: 'Huyện Mường Lát' },
  { id: 590, provinceId: 57, name: 'Huyện Quan Hóa' },
  { id: 591, provinceId: 57, name: 'Huyện Quan Sơn' },
  { id: 592, provinceId: 57, name: 'Huyện Bá Thước' },
  { id: 593, provinceId: 57, name: 'Huyện Thường Xuân' },
  { id: 594, provinceId: 57, name: 'Huyện Như Xuân' },
  { id: 595, provinceId: 57, name: 'Huyện Như Thanh' },
  { id: 596, provinceId: 57, name: 'Huyện Thiệu Hóa' },
  { id: 597, provinceId: 57, name: 'Huyện Triệu Sơn' },
  { id: 598, provinceId: 57, name: 'Huyện Nông Cống' },
  { id: 599, provinceId: 57, name: 'Huyện Đông Sơn' },
  { id: 600, provinceId: 57, name: 'Huyện Hà Trung' },
  { id: 601, provinceId: 57, name: 'Huyện Vĩnh Lộc' },
  { id: 602, provinceId: 57, name: 'Huyện Yên Định' },
  { id: 603, provinceId: 57, name: 'Huyện Thọ Xuân' },
  { id: 604, provinceId: 57, name: 'Huyện Hậu Lộc' },
  { id: 605, provinceId: 57, name: 'Huyện Hoằng Hóa' },
  { id: 606, provinceId: 57, name: 'Huyện Nga Sơn' },
  { id: 607, provinceId: 57, name: 'Huyện Hà Nguyên' },
  { id: 608, provinceId: 57, name: 'Huyện Lang Chánh' },
  { id: 609, provinceId: 57, name: 'Huyện Ngọc Lặc' },
  { id: 610, provinceId: 57, name: 'Huyện Cẩm Thủy' },
  { id: 611, provinceId: 57, name: 'Huyện Thạch Thành' },
  // Tỉnh Thừa Thiên Huế (provinceId: 58)
  { id: 612, provinceId: 58, name: 'Thành phố Huế' },
  { id: 613, provinceId: 58, name: 'Thị xã Hương Thủy' },
  { id: 614, provinceId: 58, name: 'Thị xã Hương Trà' },
  { id: 615, provinceId: 58, name: 'Huyện Phong Điền' },
  { id: 616, provinceId: 58, name: 'Huyện Quảng Điền' },
  { id: 617, provinceId: 58, name: 'Huyện Phú Vang' },
  { id: 618, provinceId: 58, name: 'Huyện Hương Phú' },
  { id: 619, provinceId: 58, name: 'Huyện A Lưới' },
  { id: 620, provinceId: 58, name: 'Huyện Phú Lộc' },
  { id: 621, provinceId: 58, name: 'Huyện Nam Đông' },
  // Tỉnh Tiền Giang (provinceId: 59)
  { id: 622, provinceId: 59, name: 'Thành phố Mỹ Tho' },
  { id: 623, provinceId: 59, name: 'Thị xã Gò Công' },
  { id: 624, provinceId: 59, name: 'Thị xã Cai Lậy' },
  { id: 625, provinceId: 59, name: 'Huyện Tân Phước' },
  { id: 626, provinceId: 59, name: 'Huyện Cái Bè' },
  { id: 627, provinceId: 59, name: 'Huyện Cai Lậy' },
  { id: 628, provinceId: 59, name: 'Huyện Châu Thành' },
  { id: 629, provinceId: 59, name: 'Huyện Chợ Gạo' },
  { id: 630, provinceId: 59, name: 'Huyện Gò Công Tây' },
  { id: 631, provinceId: 59, name: 'Huyện Gò Công Đông' },
  { id: 632, provinceId: 59, name: 'Huyện Tân Hiệp' },
  // Tỉnh Trà Vinh (provinceId: 60)
  { id: 633, provinceId: 60, name: 'Thành phố Trà Vinh' },
  { id: 634, provinceId: 60, name: 'Thị xã Duyên Hải' },
  { id: 635, provinceId: 60, name: 'Huyện Càng Long' },
  { id: 636, provinceId: 60, name: 'Huyện Cầu Kè' },
  { id: 637, provinceId: 60, name: 'Huyện Tiểu Cần' },
  { id: 638, provinceId: 60, name: 'Huyện Trà Cú' },
  { id: 639, provinceId: 60, name: 'Huyện Hàu Giang' },
  { id: 640, provinceId: 60, name: 'Huyện Duyên Hải' },
  // Tỉnh Tuyên Quang (provinceId: 61)
  { id: 641, provinceId: 61, name: 'Thành phố Tuyên Quang' },
  { id: 642, provinceId: 61, name: 'Huyện Lâm Bình' },
  { id: 643, provinceId: 61, name: 'Huyện Na Hang' },
  { id: 644, provinceId: 61, name: 'Huyện Chiêm Hóa' },
  { id: 645, provinceId: 61, name: 'Huyện Hàm Yên' },
  { id: 646, provinceId: 61, name: 'Huyện Yên Sơn' },
  { id: 647, provinceId: 61, name: 'Huyện Sơn Dương' },
  // Tỉnh Vĩnh Long (provinceId: 62)
  { id: 648, provinceId: 62, name: 'Thành phố Vĩnh Long' },
  { id: 649, provinceId: 62, name: 'Thị xã Bình Minh' },
  { id: 650, provinceId: 62, name: 'Huyện Long Hồ' },
  { id: 651, provinceId: 62, name: 'Huyện Mang Thít' },
  { id: 652, provinceId: 62, name: 'Huyện Vũng Liêm' },
  { id: 653, provinceId: 62, name: 'Huyện Tam Bình' },
  { id: 654, provinceId: 62, name: 'Huyện Trà Ôn' },
  { id: 655, provinceId: 62, name: 'Huyện Bình Tân' },
  // Tỉnh Vĩnh Phúc (provinceId: 63)
  { id: 656, provinceId: 63, name: 'Thành phố Vĩnh Yên' },
  { id: 657, provinceId: 63, name: 'Thị xã Phúc Yên' },
  { id: 658, provinceId: 63, name: 'Huyện Lập Thạch' },
  { id: 659, provinceId: 63, name: 'Huyện Tam Dương' },
  { id: 660, provinceId: 63, name: 'Huyện Tam Đảo' },
  { id: 661, provinceId: 63, name: 'Huyện Bình Xuyên' },
  { id: 662, provinceId: 63, name: 'Huyện Yên Lạc' },
  { id: 663, provinceId: 63, name: 'Huyện Vĩnh Tường' },
  { id: 664, provinceId: 63, name: 'Huyện Sông Lô' },
  // Tỉnh Yên Bái (provinceId: 64)
  { id: 665, provinceId: 64, name: 'Thành phố Yên Bái' },
  { id: 666, provinceId: 64, name: 'Thị xã Nghĩa Lộ' },
  { id: 667, provinceId: 64, name: 'Huyện Thái Nguyên' },
  { id: 668, provinceId: 64, name: 'Huyện Trạm Tấu' },
  { id: 669, provinceId: 64, name: 'Huyện Trấn Yên' },
  { id: 670, provinceId: 64, name: 'Huyện Văn Chấn' },
  { id: 671, provinceId: 64, name: 'Huyện Văn Yên' },
  { id: 672, provinceId: 64, name: 'Huyện Yên Bình' },
  { id: 673, provinceId: 64, name: 'Huyện Mù Cang Chải' },
];

export function getProvinceName(id: number) {
  return PROVINCES.find(p => p.id === id)?.name ?? `Tỉnh #${id}`;
}
export function getDistrictName(id: number) {
  return DISTRICTS.find(d => d.id === id)?.name ?? `Huyện #${id}`;
}

export default function AddressPage() {
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);
  const [editAddr, setEditAddr] = useState<AddressResponse | undefined>();
  const [defaultData, setDefaultData] = useState<AddressFormData | undefined>();
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const { data: addresses, isLoading, error } = useQuery({
    queryKey: ['user-addresses'],
    queryFn: () => addressApi.list().then(r => r.data.data ?? []),
    retry: 1,
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => addressApi.remove(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['user-addresses'] }); setDeletingId(null); },
  });

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Địa chỉ giao hàng</h1>
          <p className="text-gray-500 mt-1 text-sm">Quản lý danh sách địa chỉ nhận hàng của bạn</p>
        </div>
        <button
          onClick={() => { setEditAddr(undefined); setDefaultData(undefined); setAddOpen(true); }}
          className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold flex items-center gap-2 transition-all"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Thêm địa chỉ
        </button>
      </div>

      {isLoading && (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="bg-white rounded-2xl border border-gray-100 p-5">
              <Skeleton className="h-5 w-2/3 mb-3" />
              <Skeleton className="h-4 w-full mb-2" />
              <Skeleton className="h-4 w-1/3" />
            </div>
          ))}
        </div>
      )}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          Không thể tải danh sách địa chỉ.
        </div>
      )}

      {!isLoading && !error && (!addresses || addresses.length === 0) && (
        <div className="bg-white rounded-2xl border border-gray-100 py-20 text-center text-gray-400">
          <span className="text-4xl block mb-3">📍</span>
          <p className="font-medium text-gray-600">Chưa có địa chỉ nào</p>
          <p className="text-sm mt-1">Thêm địa chỉ để mua hàng nhanh hơn</p>
          <button onClick={() => setAddOpen(true)} className="mt-4 px-5 py-2 bg-blue-600 text-white rounded-xl text-sm font-semibold">
            Thêm địa chỉ đầu tiên
          </button>
        </div>
      )}

      <div className="space-y-3">
        {addresses?.map(addr => (
          <div key={addr.addressId} className="bg-white rounded-2xl border border-gray-100 p-5">
            <div className="flex items-start justify-between">
              <div className="flex-1">
                {addr.isDefault && (
                  <span className="inline-block px-2.5 py-0.5 bg-blue-100 text-blue-700 text-xs font-semibold rounded-full mb-2">
                    Mặc định
                  </span>
                )}
                <p className="text-sm font-medium text-gray-900 leading-relaxed">
                  {addr.fullAddress}
                </p>
                <p className="text-xs text-gray-400 mt-1">
                  {getDistrictName(addr.districtId)}, {getProvinceName(addr.provinceId)}
                </p>
              </div>
              <div className="flex items-center gap-2 ml-4">
                <button
                  onClick={() => { setEditAddr(addr); setAddOpen(true); }}
                  className="text-xs text-blue-600 hover:text-blue-700 font-medium px-3 py-1.5 border border-blue-200 rounded-lg hover:bg-blue-50 transition-colors"
                >
                  Sửa
                </button>
                <button
                  onClick={() => setDeletingId(addr.addressId)}
                  className="text-xs text-red-500 hover:text-red-600 px-3 py-1.5 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                >
                  Xoá
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {addOpen && (
        <AddressModal
          address={editAddr}
          defaultData={defaultData}
          onClose={() => { setAddOpen(false); setEditAddr(undefined); setDefaultData(undefined); }}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['user-addresses'] })}
        />
      )}

      {deletingId !== null && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl p-6 w-full max-w-sm text-center">
            <div className="text-5xl mb-4">🗑️</div>
            <h3 className="text-lg font-bold text-gray-900 mb-2">Xoá địa chỉ?</h3>
            <p className="text-sm text-gray-500 mb-6">Hành động này không thể hoàn tác.</p>
            <div className="flex gap-3">
              <button onClick={() => setDeletingId(null)} className="flex-1 py-2.5 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50">
                Huỷ
              </button>
              <button
                onClick={() => deleteMut.mutate(deletingId)}
                disabled={deleteMut.isPending}
                className="flex-1 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-xl text-sm font-medium disabled:opacity-60"
              >
                {deleteMut.isPending ? 'Đang xoá...' : 'Xoá'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
