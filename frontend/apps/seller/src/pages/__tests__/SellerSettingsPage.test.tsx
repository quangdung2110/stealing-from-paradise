import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/utils';
import SellerSettingsPage from '../SellerSettingsPage';
import { userApi } from '@shared/api/user.api';
import { sellerApi } from '@shared/api/seller.api';

vi.mock('@shared/api/user.api', () => ({
  userApi: {
    getProfile: vi.fn(),
    updateProfile: vi.fn(() => Promise.resolve({ data: { data: {} } })),
    changePassword: vi.fn(() => Promise.resolve({ data: { data: {} } })),
  },
}));

vi.mock('@shared/api/seller.api', () => ({
  sellerApi: {
    getStripeStatus: vi.fn(() => Promise.resolve({ data: { data: null } })),
  },
}));

const profile = {
  userId: 1, username: 'shop', email: 'shop@e.com', fullName: 'Cửa hàng A', phone: '0900000000',
  roles: ['SELLER'], status: 'ACTIVE', createdAt: '2026-01-01', updatedAt: '2026-01-01',
};

beforeEach(() => {
  vi.clearAllMocks();
  (userApi.getProfile as any).mockResolvedValue({ data: { data: profile } });
});

describe('SellerSettingsPage', () => {
  it('renders the seller profile', async () => {
    renderWithProviders(<SellerSettingsPage />, { route: '/settings' });
    expect((await screen.findAllByText('shop@e.com')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Cửa hàng A').length).toBeGreaterThan(0);
  });

  it('saves edited profile via updateProfile', async () => {
    renderWithProviders(<SellerSettingsPage />, { route: '/settings' });
    fireEvent.click(await screen.findByText(/Chỉnh sửa/));
    fireEvent.change(screen.getByPlaceholderText(/Nhập tên cửa hàng/i), { target: { value: 'Cửa hàng B' } });
    fireEvent.click(screen.getByRole('button', { name: /^Lưu$/ }));
    await waitFor(() => expect(userApi.updateProfile).toHaveBeenCalledWith(
      expect.objectContaining({ fullName: 'Cửa hàng B' }),
    ));
  });

  it('calls changePassword when form is filled and submitted', async () => {
    renderWithProviders(<SellerSettingsPage />, { route: '/settings' });

    const currentInput = await screen.findByPlaceholderText('Nhập mật khẩu');
    const newInput = screen.getByPlaceholderText('Ít nhất 6 ký tự');
    const confirmInput = screen.getByPlaceholderText('Nhập lại mật khẩu mới');

    fireEvent.change(currentInput, { target: { value: 'oldpass123' } });
    fireEvent.change(newInput, { target: { value: 'newpass123' } });
    fireEvent.change(confirmInput, { target: { value: 'newpass123' } });

    const submitBtn = screen.getByRole('button', { name: 'Đổi mật khẩu' });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(userApi.changePassword).toHaveBeenCalledWith({
        currentPassword: 'oldpass123',
        newPassword: 'newpass123',
      });
    });

    expect(await screen.findByText('Đổi mật khẩu thành công!')).toBeInTheDocument();
  });
});
