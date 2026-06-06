package com.techdecide.api.dto.decision;

import com.techdecide.api.dto.tag.TagDTO;
import com.techdecide.api.entity.Decision.Status;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DecisionDTO {

    private Long id;
    private String title;
    private String context;
    private String decision;
    private String consequences;
    private Status status;
    private String authorName;
    private String teamName;
    private List<TagDTO> tags;
    private List<AlternativeDTO> alternatives;
    private Long supersededById;
    private LocalDateTime reviewDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class AlternativeDTO {
        private Long id;
        private String name;
        private String rejectionReason;
    }
}