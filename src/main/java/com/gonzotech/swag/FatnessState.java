package com.gonzotech.swag;

/**
 * Мост «серверная жирность → клиентский рендер»: mixin-ами добавляется в
 * {@code CatRenderState} и {@code WolfRenderState}; renderer-mixin заполняет,
 * model-mixin читает. Отделён от {@link PetFatness}, чтобы клиентским миксинам
 * не нужен был доступ к сущности.
 */
public interface FatnessState {

    /** Множитель торса для текущего кадра (1.0..2.3). */
    float gonzotech$fatness();

    void gonzotech$setFatness(float value);
}
