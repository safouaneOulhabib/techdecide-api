package com.techdecide.api.repository;

import com.techdecide.api.entity.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class TagRepositoryTest {

    @Autowired TagRepository tagRepository;

    private Tag save(String name, String color) {
        return tagRepository.save(Tag.builder().name(name).color(color).build());
    }

    @Test
    void findByName_existingName_returnsTag() {
        save("backend", "#ff0000");

        Optional<Tag> result = tagRepository.findByName("backend");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("backend");
        assertThat(result.get().getColor()).isEqualTo("#ff0000");
    }

    @Test
    void findByName_nonExistingName_returnsEmpty() {
        Optional<Tag> result = tagRepository.findByName("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByName_existingName_returnsTrue() {
        save("backend", "#ff0000");

        assertThat(tagRepository.existsByName("backend")).isTrue();
    }

    @Test
    void existsByName_nonExistingName_returnsFalse() {
        assertThat(tagRepository.existsByName("nosuchTag")).isFalse();
    }

    @Test
    void existsByName_caseSensitive_returnsFalseForWrongCase() {
        save("backend", "#ff0000");

        assertThat(tagRepository.existsByName("Backend")).isFalse();
    }

    @Test
    void save_withNullColor_savesSuccessfully() {
        Tag tag = tagRepository.save(Tag.builder().name("nocolor").build());

        assertThat(tag.getId()).isNotNull();
        assertThat(tag.getColor()).isNull();
    }

    @Test
    void save_duplicateName_throwsException() {
        save("backend", "#ff0000");

        assertThrows(Exception.class, () -> {
            tagRepository.save(Tag.builder().name("backend").color("#00ff00").build());
            tagRepository.flush();
        });
    }

    @Test
    void findAll_returnsAllSavedTags() {
        save("backend", "#ff0000");
        save("frontend", "#00ff00");
        save("devops", "#0000ff");

        assertThat(tagRepository.findAll()).hasSize(3);
    }
}
