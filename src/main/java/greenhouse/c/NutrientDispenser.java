package greenhouse.c;

import greenhouse.storage.GreenhouseException;

/** Subsystem C: checks availability of nutrients before issuing a dose. */
public final class NutrientDispenser {
    public int doseMg(int liters, int availableMg) {
        if (liters <= 0 || availableMg < 0) throw new GreenhouseException("Invalid nutrient request");
        int neededMg = liters * 100;
        if (availableMg < neededMg)
            throw new GreenhouseException("C failure: insufficient nutrient stock; need " + neededMg + " mg");
        return neededMg;
    }
}
