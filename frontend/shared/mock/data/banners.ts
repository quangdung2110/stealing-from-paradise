import type { Banner } from '../../api/banner.api';

export const MOCK_BANNERS: Banner[] = [
  {
    id: 'bn-1',
    title: 'Siêu Sale Mùa Hè',
    imageUrl: 'https://placehold.co/1200x400/2563eb/FFF?text=Sieu+Sale+Mua+He',
    position: 'HERO',
    active: true,
    startsAt: null,
    endsAt: null,
  },
  {
    id: 'bn-2',
    title: 'Banner Tết (đang tắt)',
    imageUrl: 'https://placehold.co/1200x400/dc2626/FFF?text=Tet+2027',
    position: 'HERO',
    active: false,
    startsAt: '2027-01-20T00:00:00Z',
    endsAt: '2027-02-20T00:00:00Z',
  },
];
