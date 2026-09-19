package com.gonzotech.swag;

/**
 * API «жирности» питомца (кот/волк). Реализуется миксинами на {@code Cat} и
 * {@code Wolf}: значение `fatness` — множитель торса (1.0 = обычный питомец,
 * максимум {@link FatPetLogic#MAX_FATNESS}). Хранится в SynchedEntityData
 * (синхронизация с клиентом бесплатная) + персист NBT-ключами Gonzo*.
 */
public interface PetFatness {

    /** Текущий множитель торса (1.0..2.3). */
    float gonzotech$fatness();

    void gonzotech$setFatness(float value);

    /** gameTime последнего реального приёма пищи из миски. */
    long gonzotech$lastEatAt();

    void gonzotech$setLastEatAt(long gameTime);

    /** gameTime последнего захода к ПУСТОЙ миске (анти-зацикливание). */
    long gonzotech$lastEmptyVisitAt();

    void gonzotech$setLastEmptyVisitAt(long gameTime);

    /** Внутренний счётчик секунды для сдувания (0..19). */
    int gonzotech$deflateCounter();

    void gonzotech$setDeflateCounter(int value);
}
