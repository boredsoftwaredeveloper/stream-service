package dev.bored.stream.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bored.stream.dto.*;
import dev.bored.stream.entity.FeedPost;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;
import java.util.Map;

/**
 * MapStruct mapper that converts between {@link FeedPost} entities and {@link FeedPostDTO} objects.
 *
 * <p>JSONB columns ({@code code_snippet}, {@code image_content}) are stored as
 * {@code Map<String, Object>} in the entity. Custom qualifiers convert them
 * to/from their strongly-typed DTO counterparts using Jackson.</p>
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class FeedPostMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ── Entity → DTO ────────────────────────────────────────────────

    /**
     * Converts a {@link FeedPost} entity to a {@link FeedPostDTO}.
     *
     * @param entity the entity to convert
     * @return the corresponding DTO
     */
    @Mapping(target = "id", expression = "java(entity.getPostId() != null ? String.valueOf(entity.getPostId()) : null)")
    @Mapping(source = "codeSnippet", target = "codeSnippet", qualifiedByName = "mapToCodeSnippetDTO")
    @Mapping(source = "imageContent", target = "imageContent", qualifiedByName = "mapToImageContentDTO")
    public abstract FeedPostDTO toDTO(FeedPost entity);

    /**
     * Converts a list of entities to a list of DTOs.
     *
     * @param entities the list to convert
     * @return the corresponding list of DTOs
     */
    public abstract List<FeedPostDTO> toDTOList(List<FeedPost> entities);

    // ── DTO → Entity ────────────────────────────────────────────────

    /**
     * Converts a {@link FeedPostDTO} to a {@link FeedPost} entity.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     */
    @Mapping(target = "postId", ignore = true)
    @Mapping(source = "codeSnippet", target = "codeSnippet", qualifiedByName = "codeSnippetDTOToMap")
    @Mapping(source = "imageContent", target = "imageContent", qualifiedByName = "imageContentDTOToMap")
    public abstract FeedPost toEntity(FeedPostDTO dto);

    // ── Qualifier methods ───────────────────────────────────────────

    @Named("mapToCodeSnippetDTO")
    public CodeSnippetDTO mapToCodeSnippetDTO(Map<String, Object> map) {
        if (map == null) return null;
        return OBJECT_MAPPER.convertValue(map, CodeSnippetDTO.class);
    }

    @Named("mapToImageContentDTO")
    public ImageContentDTO mapToImageContentDTO(Map<String, Object> map) {
        if (map == null) return null;
        return OBJECT_MAPPER.convertValue(map, ImageContentDTO.class);
    }

    @Named("codeSnippetDTOToMap")
    public Map<String, Object> codeSnippetDTOToMap(CodeSnippetDTO dto) {
        if (dto == null) return null;
        return OBJECT_MAPPER.convertValue(dto, new TypeReference<>() {});
    }

    @Named("imageContentDTOToMap")
    public Map<String, Object> imageContentDTOToMap(ImageContentDTO dto) {
        if (dto == null) return null;
        return OBJECT_MAPPER.convertValue(dto, new TypeReference<>() {});
    }
}
