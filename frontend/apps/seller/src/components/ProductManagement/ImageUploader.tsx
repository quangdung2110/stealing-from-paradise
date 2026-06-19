import { useState, useRef, useCallback } from 'react';
import { sellerApi } from '@shared/api/seller.api';

export default function ImageUploader({
  productId,
  images,
  onChange,
}: {
  productId: string;
  images: string[];
  onChange: (imgs: string[]) => void;
}) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [dragOver, setDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleUpload = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setUploading(true);
    setError('');
    try {
      const newImages: string[] = [];
      for (const file of Array.from(files)) {
        if (!file.type.startsWith('image/')) continue;
        const { data: resp } = await sellerApi.getPresignedUrl(productId, file.name, file.type);
        const result = resp.data;
        if (!result) throw new Error('Missing presigned URL response');
        await fetch(result.presignedUrl, { method: 'PUT', body: file, headers: { 'Content-Type': file.type } });
        newImages.push(result.objectUrl);
      }
      onChange([...images, ...newImages]);
    } catch (e: any) {
      setError('Tải ảnh thất bại. Vui lòng thử lại.');
    } finally {
      setUploading(false);
      setDragOver(false);
    }
  };

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    handleUpload(e.dataTransfer.files);
  }, [images, productId]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1.5">Hình ảnh sản phẩm</label>

      {/* Preview grid */}
      {images.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-3">
          {images.map((url, i) => (
            <div key={i} className="relative w-20 h-20 rounded-xl overflow-hidden border border-gray-200 group">
              <img src={url} alt="" className="w-full h-full object-cover" />
              <button
                onClick={() => onChange(images.filter((_, j) => j !== i))}
                className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
              >
                <span className="w-6 h-6 bg-red-500 text-white rounded-full text-sm flex items-center justify-center">×</span>
              </button>
              {i === 0 && (
                <span className="absolute bottom-1 left-1 bg-blue-600 text-white text-[10px] px-1.5 py-0.5 rounded font-medium">
                  Chính
                </span>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Drop zone */}
      <div
        onDragOver={e => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        className={`
          border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-all
          ${dragOver ? 'border-blue-400 bg-blue-50' : 'border-gray-300 hover:border-gray-400 bg-gray-50/30'}
        `}
      >
        {uploading ? (
          <div className="flex flex-col items-center gap-2">
            <div className="w-8 h-8 border-3 border-blue-500 border-t-transparent rounded-full animate-spin" />
            <span className="text-sm text-gray-500">Đang tải ảnh lên...</span>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-1">
            <span className="text-3xl text-gray-300">📷</span>
            <p className="text-sm font-medium text-gray-600">Kéo thả ảnh vào đây hoặc nhấn để chọn</p>
            <p className="text-xs text-gray-400">JPEG, PNG, WebP tối đa 5MB</p>
          </div>
        )}
        <input
          ref={inputRef}
          type="file"
          multiple
          accept="image/*"
          className="hidden"
          onChange={e => handleUpload(e.target.files)}
        />
      </div>

      {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
      {images.length > 0 && (
        <p className="text-xs text-gray-400 mt-1.5">{images.length} ảnh · Ảnh đầu tiên là ảnh chính</p>
      )}
    </div>
  );
}
