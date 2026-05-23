package com.techdecide.api.dto.comment;

import com.techdecide.api.entity.Comment.Vote;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotBlank(message = "Content is required")
    private String content;

    private Vote vote;
}
