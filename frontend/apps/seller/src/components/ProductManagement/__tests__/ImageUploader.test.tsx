import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import ImageUploader from '../ImageUploader';
import { sellerApi } from '@shared/api/seller.api';

vi.mock('@shared/api/seller.api', () => ({
  sellerApi: {
    getPresignedUrl: vi.fn(() => Promise.resolve({
      data: { data: { presignedUrl: 'http://put/x', objectUrl: 'http://cdn/x.png', expiresIn: 60 } },
    })),
  },
}));

beforeEach(() => {
  vi.clearAllMocks();
  // The component PUTs the file to the presigned URL via fetch.
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: true } as Response)));
});

describe('ImageUploader (UC-PRODUCT-005)', () => {
  it('requests a presigned URL and reports the uploaded object URL', async () => {
    const onChange = vi.fn();
    const { container } = render(<ImageUploader productId="p1" images={[]} onChange={onChange} />);
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['x'], 'photo.png', { type: 'image/png' });
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => expect(sellerApi.getPresignedUrl).toHaveBeenCalledWith('p1', 'photo.png', 'image/png'));
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(['http://cdn/x.png']));
    expect(global.fetch).toHaveBeenCalledWith('http://put/x', expect.objectContaining({ method: 'PUT' }));
  });
});
