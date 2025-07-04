package com.store.backend.product.implement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.store.backend.category.CategoryEntity;
import com.store.backend.category.CategoryRepository;
import com.store.backend.common.SlugUtil;
import com.store.backend.exception.AlreadyExistsException;
import com.store.backend.exception.BadRequestException;
import com.store.backend.exception.NotFoundException;
import com.store.backend.product.ProductEntity;
import com.store.backend.product.ProductRepository;
import com.store.backend.product.ProductService;
import com.store.backend.product.mapper.ProductMapper;
import com.store.backend.product.request.CreateProductRequest;
import com.store.backend.product.request.UpdateProductRequest;
import com.store.backend.product.response.PagedResponse;
import com.store.backend.product.response.ProductResponse;

import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductServiceImpl implements ProductService {
  ProductRepository productRepository;
  CategoryRepository categoryRepository;
  ProductMapper productMapper;

  @Override
  public PagedResponse<ProductResponse> getAllProducts(int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<ProductEntity> productPage = productRepository.findAll(pageable);
    List<ProductResponse> productResponses = productPage.getContent().stream().map(productMapper::entityToResponse)
        .toList();
    return new PagedResponse<>(productResponses, productPage.getNumber(), productPage.getSize(),
        productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast());
  }

  @Override
  @Transactional
  public ProductEntity createProduct(CreateProductRequest request) {
    String slug = SlugUtil.toSlug(request.getTitle());
    if (productRepository.existsBySlug(slug)) {
      throw new AlreadyExistsException("Sản phẩm đã tồn tại");
    }

    Set<CategoryEntity> categories = categoryRepository.findAllByIdIn(request.getCategoryIds());
    if (categories.size() != request.getCategoryIds().size()) {
      throw new BadRequestException("Có ID danh mục không hợp lệ");
    }

    LocalDate reqStartSale = request.getStartSale();
    LocalDate reqEndSale = request.getEndSale();
    BigDecimal reqSalePrice = request.getSalePrice();
    if (request.isSaleProduct()) {
      if (reqSalePrice == null) {
        throw new BadRequestException("Vui lòng nhập giá khuyến mãi");
      }
      if (reqSalePrice.compareTo(request.getPrice()) > 0) {
        throw new BadRequestException("Giá khuyến mãi phải nhỏ hơn giá gốc");
      }
      if (reqStartSale == null && reqEndSale == null) {
        throw new BadRequestException("Vui lòng nhập ngày bắt đầu và kết thúc khuyến mãi");
      }
      if (reqEndSale == null) {
        throw new BadRequestException("Vui lòng nhập ngày kết thúc khuyến mãi");
      }

      LocalDate today = LocalDate.now();
      if (reqStartSale != null && reqStartSale.isBefore(today)) {
        throw new BadRequestException("Thời gian bắt đầu sale không được nhỏ hơn hôm nay");
      }
      if (!reqEndSale.isAfter(today)) {
        throw new BadRequestException("Ngày kết thúc khuyến mãi phải lớn hơn hôm nay ít nhất 1 ngày");
      }
      if (reqStartSale == null && reqEndSale != null) {
        request.setStartSale(today);
      }
      if (reqStartSale != null && reqEndSale != null && !reqStartSale.isBefore(reqEndSale)) {
        throw new BadRequestException("Ngày bắt đầu khuyến mãi phải trước ngày kết thúc");
      }
    } else {
      if (reqSalePrice != null) {
        throw new BadRequestException("Không có khuyến mãi nên không thể nhập giá khuyến mãi");
      }
      if (request.getEndSale() != null) {
        throw new BadRequestException("Không có khuyến mãi nên không thể nhập ngày kết thúc khuyến mãi");
      }
      if (request.getStartSale() != null) {
        throw new BadRequestException("Không có khuyến mãi nên không thể nhập ngày bắt đầu khuyến mãi");
      }
    }

    ProductEntity newProduct = ProductEntity.builder().title(request.getTitle()).slug(slug)
        .description(request.getDescription()).price(request.getPrice()).saleProduct(request.isSaleProduct())
        .salePrice(request.getSalePrice()).startSale(request.getStartSale()).endSale(request.getEndSale())
        .categories(categories).build();
    return productRepository.save(newProduct);
  }

  @Override
  @Transactional
  public ProductEntity getProductBySlug(String slug) {
    return productRepository.findBySlug(slug).orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm"));
  }

  @Override
  @Transactional
  public ProductEntity updateProduct(String id, UpdateProductRequest request) {
    ProductEntity product = productRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm"));
    if (request.getTitle() != null && !request.getTitle().equals(product.getTitle())) {
      String newSlug = SlugUtil.toSlug(request.getTitle());
      if (productRepository.existsBySlug(newSlug)) {
        throw new AlreadyExistsException("Sản phẩm đã tồn tại");
      }
      product.setTitle(request.getTitle());
      product.setSlug(newSlug);
    }

    if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
      Set<CategoryEntity> categories = categoryRepository.findAllByIdIn(request.getCategoryIds());
      if (categories.size() != request.getCategoryIds().size()) {
        throw new BadRequestException("Có ID danh mục không hợp lệ");
      }
      product.setCategories(categories);
    }

    if (request.getDescription() != null)
      product.setDescription(request.getDescription());
    if (request.getPrice() != null)
      product.setPrice(request.getPrice());

    product.setSaleProduct(request.isSaleProduct());
    if (request.isSaleProduct()) {
      if (request.getSalePrice() == null) {
        throw new BadRequestException("Vui lòng nhập giá khuyến mãi");
      }

      BigDecimal basePrice = request.getPrice() != null ? request.getPrice() : product.getPrice();
      if (request.getSalePrice().compareTo(basePrice) > 0) {
        throw new BadRequestException("Giá khuyến mãi phải nhỏ hơn giá gốc");
      }
      if (request.getStartSale() == null && request.getEndSale() == null) {
        throw new BadRequestException("Vui lòng nhập ngày bắt đầu và kết thúc khuyến mãi");
      }
      if (request.getEndSale() == null) {
        throw new BadRequestException("Vui lòng nhập ngày kết thúc khuyến mãi");
      }

      LocalDate today = LocalDate.now();
      if (request.getStartSale() != null && request.getStartSale().isBefore(today)) {
        throw new BadRequestException("Thời gian bắt đầu sale không được nhỏ hơn hôm nay");
      }
      if (request.getEndSale() != null && !request.getEndSale().isAfter(today)) {
        throw new BadRequestException("Ngày kết thúc khuyến mãi phải lớn hơn hôm nay ít nhất 1 ngày");
      }

      LocalDate startSale = request.getStartSale() != null ? request.getStartSale() : today;
      product.setSalePrice(request.getSalePrice());
      product.setStartSale(startSale);
      product.setEndSale(request.getEndSale());
    } else {
      if (request.getSalePrice() != null || request.getStartSale() != null || request.getEndSale() != null)
        throw new BadRequestException("Không có khuyến mãi nên không thể nhập các thông tin sale");
      product.setSalePrice(null);
      product.setStartSale(null);
      product.setEndSale(null);
    }

    return productRepository.save(product);
  }

  @Override
  @Transactional
  public void deleteProduct(String id) {
    if (!productRepository.existsById(id)) {
      throw new NotFoundException("Không tìm thấy sản phẩm");
    }
    productRepository.deleteById(id);
  }
}
