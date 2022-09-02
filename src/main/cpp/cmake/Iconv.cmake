set(ICONV_SOURCES
  libiconv-gnu/libcharset/lib/localcharset.c
  libiconv-gnu/lib/iconv.c
  libiconv-gnu/lib/relocatable.c
)
add_library(iconv STATIC ${ICONV_SOURCES})
add_library(Iconv::Iconv ALIAS iconv)
target_compile_definitions(iconv PRIVATE
  LIBDIR="c" BUILDING_LIBICONV IN_LIBRARY
)
target_include_directories(iconv PRIVATE
  "libiconv/lib"
  "libiconv/libcharset/include"
)
target_include_directories(iconv PUBLIC
  $<BUILD_INTERFACE:${CMAKE_SOURCE_DIR}/libiconv/include>
)
