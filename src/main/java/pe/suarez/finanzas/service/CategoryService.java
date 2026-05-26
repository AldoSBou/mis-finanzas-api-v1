package pe.suarez.finanzas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.suarez.finanzas.api.ErrorCode;
import pe.suarez.finanzas.domain.AllocationBucket;
import pe.suarez.finanzas.domain.Category;
import pe.suarez.finanzas.dto.CategoryDtos.*;
import pe.suarez.finanzas.exception.ApiException;
import pe.suarez.finanzas.mapper.Mappers;
import pe.suarez.finanzas.repository.CategoryRepository;
import pe.suarez.finanzas.security.UserContext;

import java.util.List;

@ApplicationScoped
public class CategoryService {

    @Inject CategoryRepository repository;
    @Inject UserContext userContext;

    public List<CategoryResponse> listAll(boolean includeArchived) {
        return repository.listForUser(userContext.userId(), includeArchived).stream()
                .map(Mappers::toCategoryResponse)
                .toList();
    }

    public CategoryResponse get(Long id) {
        Category c = repository.findByIdForUser(id, userContext.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
        return Mappers.toCategoryResponse(c);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        Category c = new Category();
        c.userId = userContext.userId();
        c.name = req.name().trim();
        c.type = req.type();
        c.defaultBucket = req.defaultBucket() != null ? req.defaultBucket() : AllocationBucket.UNCATEGORIZED;
        c.color = req.color();
        c.icon = req.icon();
        c.persist();
        return Mappers.toCategoryResponse(c);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = repository.findByIdForUser(id, userContext.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
        c.name = req.name().trim();
        c.type = req.type();
        c.defaultBucket = req.defaultBucket() != null ? req.defaultBucket() : c.defaultBucket;
        c.color = req.color();
        c.icon = req.icon();
        return Mappers.toCategoryResponse(c);
    }

    @Transactional
    public void archive(Long id) {
        Category c = repository.findByIdForUser(id, userContext.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
        c.archived = true;
    }
}
