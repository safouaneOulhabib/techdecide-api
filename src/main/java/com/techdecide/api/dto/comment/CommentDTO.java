package com.techdecide.api.dto.comment;

import com.techdecide.api.entity.Comment.Vote;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentDTO {

    private Long id;
    private String content;
    private Vote vote;
    private String authorName;
    private Long decisionId;
    private LocalDateTime createdAt;
}
