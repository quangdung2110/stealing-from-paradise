export const MOCK_CATEGORIES = [
  { id: 'cat_1', name: 'Thiết bị điện tử', slug: 'thiet-bi-dien-tu', description: 'Điện thoại, Laptop, Máy tính bảng', parentId: null, productCount: 15 },
  { id: 'cat_2', name: 'Thời trang', slug: 'thoi-trang', description: 'Quần áo, giày dép, phụ kiện', parentId: null, productCount: 20 },
  { id: 'cat_3', name: 'Gia dụng', slug: 'gia-dung', description: 'Đồ dùng nhà bếp, phòng khách', parentId: null, productCount: 8 },
  { id: 'cat_4', name: 'Điện thoại', slug: 'dien-thoai', description: 'Điện thoại thông minh các hãng', parentId: 'cat_1', productCount: 10 },
  { id: 'cat_5', name: 'Laptop', slug: 'laptop', description: 'Laptop văn phòng, gaming', parentId: 'cat_1', productCount: 5 }
];
