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
    private Long authorId;
    private String authorName;
    private Long projectId;
    private String projectName;
    private List<TeamRef> teams;
    private List<TagDTO> tags;
    private List<AlternativeDTO> alternatives;
    private Long supersededById;
    private String supersededByTitle;
    private LocalDateTime reviewDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean canVote;
    private boolean canGovern;

    @Data
    @Builder
    public static class TeamRef {
        private Long teamId;
        private String teamName;
    }

    @Data
    @Builder
    public static class AlternativeDTO {
        private Long id;
        private String name;
        private String rejectionReason;
    }
}
