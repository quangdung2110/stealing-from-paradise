package com.flashsale.productservice.dto.banner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerRequest {

    @NotBlank(message = "Tiêu đề banner không được để trống")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "URL ảnh banner không được để trống")
    @Size(max = 500)
    private String imageUrl;

    @Builder.Default
    private String position = "HERO";

    @Builder.Default
    private Boolean active = true;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;
}
