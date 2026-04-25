package dev.bored.stream.mapper;

import dev.bored.stream.dto.CreatePostRequest;
import dev.bored.stream.dto.PostAuthorDTO;
import dev.bored.stream.dto.PostDTO;
import dev.bored.stream.entity.AppUser;
import dev.bored.stream.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for {@link Post} and {@link AppUser} entities ↔ DTOs.
 *
 * <p>The post-to-DTO mapping takes both the {@link Post} and its author
 * {@link AppUser} as inputs. Author hydration is the caller's
 * responsibility — typically a single batch load of all distinct
 * author ids before mapping a list of posts.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PostMapper {

    /**
     * Builds a {@link PostDTO} from a {@link Post} entity and its
     * resolved {@link AppUser} author.
     *
     * @param post   the post entity
     * @param author the author — must match {@code post.authorId}
     * @return the outbound DTO
     */
    @Mapping(target = "postId", source = "post.postId")
    @Mapping(target = "caption", source = "post.caption")
    @Mapping(target = "codeBody", source = "post.codeBody")
    @Mapping(target = "codeLanguage", source = "post.codeLanguage")
    @Mapping(target = "createdAt", source = "post.createdAt")
    @Mapping(target = "author", source = "author")
    PostDTO toDTO(Post post, AppUser author);

    /** Author projection used inside {@link #toDTO(Post, AppUser)}. */
    PostAuthorDTO toAuthorDTO(AppUser author);

    /**
     * Builds a fresh {@link Post} entity from a create request.
     * Author id, generated id, timestamps and tombstone are all set
     * by the service layer, not the mapper.
     */
    @Mapping(target = "postId", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Post toEntity(CreatePostRequest req);
}
