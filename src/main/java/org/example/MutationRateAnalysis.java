package org.example;

import io.jenetics.*;
import io.jenetics.engine.*;
import io.jenetics.util.Seq;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class MutationRateAnalysis {

    private static final int CHROMOSOME_LENGTH = 10;
    private static final int POPULATION_SIZE = 1000;
    private static final int MAX_GENERATIONS = 100;
    private static final double OPTIMAL_FITNESS = 10.0;

    // Fitness function: count the number of ones
    private static int fitness(Genotype<BitGene> gt) {
        return (int) gt.chromosome().stream()
                .filter(BitGene::bit)
                .count();
    }

    // Custom alterer that changes mutation probability based on generation
    private static class AdaptiveMutator extends Mutator<BitGene, Integer> {
        private final MutationStrategy strategy;

        public AdaptiveMutator(MutationStrategy strategy) {
            super(0.01); // Default low mutation
            this.strategy = strategy;
        }

        @Override
        public AltererResult<BitGene, Integer> alter(Seq<Phenotype<BitGene, Integer>> population, long generation) {
            int currentGeneration = (int) generation;
            double mutationProb = strategy.getMutationProbability(currentGeneration);

            // Create a new mutator with the appropriate probability
            Mutator<BitGene, Integer> mutator = new Mutator<>(mutationProb);
            return mutator.alter(population, generation);
        }
    }

    // Strategy interface for different mutation approaches
    interface MutationStrategy {
        double getMutationProbability(int generation);
        String getName();
    }

    // Baseline: constant low mutation rate
    static class BaselineStrategy implements MutationStrategy {
        @Override
        public double getMutationProbability(int generation) {
            return 0.01; // 1% mutation probability
        }

        @Override
        public String getName() {
            return "Baseline (Constant 1%)";
        }
    }

    // High mutation at start (iterations 1-20)
    static class HighMutationStartStrategy implements MutationStrategy {
        @Override
        public double getMutationProbability(int generation) {
            if (generation <= 20) {
                return 0.3; // 30% mutation probability at start
            }
            return 0.01; // 1% for the rest
        }

        @Override
        public String getName() {
            return "High Mutation Start (30% gen 1-20)";
        }
    }

    // High mutation at end (iterations 90-100)
    static class HighMutationEndStrategy implements MutationStrategy {
        @Override
        public double getMutationProbability(int generation) {
            if (generation >= 90) {
                return 0.3; // 30% mutation probability at end
            }
            return 0.01; // 1% for the rest
        }

        @Override
        public String getName() {
            return "High Mutation End (30% gen 90-100)";
        }
    }

    // Results container
    static class SimulationResult {
        String strategyName;
        List<Double> bestFitnessPerGen = new ArrayList<>();
        List<Double> avgFitnessPerGen = new ArrayList<>();
        List<Integer> finalPopulationFitness = new ArrayList<>();
        double finalBestFitness;
        double finalAvgFitness;
        double convergenceRate;
        int generationsToOptimal;

        public void printMetrics() {
            System.out.println("\n=== " + strategyName + " ===");
            System.out.println("Final Best Fitness: " + finalBestFitness);
            System.out.println("Final Average Fitness: " + String.format("%.2f", finalAvgFitness));
            System.out.println("Convergence Rate: " + String.format("%.2f%%", convergenceRate * 100));
            System.out.println("Generations to Optimal: " +
                    (generationsToOptimal == -1 ? "Never reached" : generationsToOptimal));
            System.out.println("Solutions at Optimal (10): " +
                    finalPopulationFitness.stream().filter(f -> f == 10).count());
            System.out.println("Solutions >= 8: " +
                    finalPopulationFitness.stream().filter(f -> f >= 8).count());
        }
    }

    // Run simulation with a specific strategy
    private static SimulationResult runSimulation(MutationStrategy strategy) {
        SimulationResult result = new SimulationResult();
        result.strategyName = strategy.getName();

        // Create the genetic algorithm engine
        Engine<BitGene, Integer> engine = Engine
                .builder(MutationRateAnalysis::fitness, BitChromosome.of(CHROMOSOME_LENGTH))
                .populationSize(POPULATION_SIZE)
                .alterers(new AdaptiveMutator(strategy), new SinglePointCrossover<>(0.2))
                .selector(new TournamentSelector<>(5))
                .maximizing()
                .build();

        // Track when optimal is first reached
        result.generationsToOptimal = -1;

        // Evolution statistics
        EvolutionResult<BitGene, Integer> finalResult = engine.stream()
                .limit(MAX_GENERATIONS)
                .peek(er -> {
                    // Track best and average fitness
                    result.bestFitnessPerGen.add((double) er.bestFitness());
                    result.avgFitnessPerGen.add(er.population().stream()
                            .mapToInt(Phenotype::fitness)
                            .average()
                            .orElse(0.0));

                    // Check if optimal reached
                    if (result.generationsToOptimal == -1 && er.bestFitness() == OPTIMAL_FITNESS) {
                        result.generationsToOptimal = (int) er.generation();
                    }
                })
                .collect(EvolutionResult.toBestEvolutionResult());

        // Collect final population fitness
        result.finalPopulationFitness = finalResult.population().stream()
                .map(Phenotype::fitness)
                .collect(Collectors.toList());

        result.finalBestFitness = finalResult.bestFitness();
        result.finalAvgFitness = result.finalPopulationFitness.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        // Calculate convergence rate (how many solutions are near optimal)
        long nearOptimal = result.finalPopulationFitness.stream()
                .filter(f -> f >= 8)
                .count();
        result.convergenceRate = (double) nearOptimal / POPULATION_SIZE;

        return result;
    }

    // Create fitness distribution histogram
    private static JPanel createHistogram(Map<String, SimulationResult> results) {
        HistogramDataset dataset = new HistogramDataset();

        for (Map.Entry<String, SimulationResult> entry : results.entrySet()) {
            double[] values = entry.getValue().finalPopulationFitness.stream()
                    .mapToDouble(Integer::doubleValue)
                    .toArray();
            dataset.addSeries(entry.getKey(), values, 11, -0.5, 10.5);
        }

        JFreeChart chart = ChartFactory.createHistogram(
                "Final Population Fitness Distribution",
                "Fitness Value",
                "Frequency",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        return new ChartPanel(chart);
    }

    // Create average fitness evolution chart
    private static JPanel createFitnessEvolutionChart(Map<String, SimulationResult> results) {
        XYSeriesCollection dataset = new XYSeriesCollection();

        for (Map.Entry<String, SimulationResult> entry : results.entrySet()) {
            XYSeries series = new XYSeries(entry.getKey());
            SimulationResult result = entry.getValue();
            for (int i = 0; i < result.avgFitnessPerGen.size(); i++) {
                series.add(i, result.avgFitnessPerGen.get(i));
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Average Fitness Over Generations",
                "Generation",
                "Average Fitness",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        return new ChartPanel(chart);
    }

    // Create the best fitness evolution chart
    private static JPanel createBestFitnessChart(Map<String, SimulationResult> results) {
        XYSeriesCollection dataset = new XYSeriesCollection();

        for (Map.Entry<String, SimulationResult> entry : results.entrySet()) {
            XYSeries series = new XYSeries(entry.getKey());
            SimulationResult result = entry.getValue();
            for (int i = 0; i < result.bestFitnessPerGen.size(); i++) {
                series.add(i, result.bestFitnessPerGen.get(i));
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Best Fitness Over Generations",
                "Generation",
                "Best Fitness",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        return new ChartPanel(chart);
    }

    public static void main(String[] args) {
        System.out.println("Starting Genetic Algorithm Mutation Rate Analysis");
        System.out.println("Chromosome Length: " + CHROMOSOME_LENGTH);
        System.out.println("Population Size: " + POPULATION_SIZE);
        System.out.println("Max Generations: " + MAX_GENERATIONS);
        System.out.println("Optimal Fitness: " + OPTIMAL_FITNESS);

        // Define strategies
        List<MutationStrategy> strategies = Arrays.asList(
                new BaselineStrategy(),
                new HighMutationStartStrategy(),
                new HighMutationEndStrategy()
        );

        // Run simulations
        Map<String, SimulationResult> results = new LinkedHashMap<>();
        for (MutationStrategy strategy : strategies) {
            System.out.println("\nRunning: " + strategy.getName());
            SimulationResult result = runSimulation(strategy);
            results.put(strategy.getName(), result);
            result.printMetrics();
        }

        // Create GUI with charts
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Genetic Algorithm Mutation Analysis");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new GridLayout(2, 2));

            frame.add(createHistogram(results));
            frame.add(createFitnessEvolutionChart(results));
            frame.add(createBestFitnessChart(results));

            // Add metrics panel
            JPanel metricsPanel = new JPanel();
            metricsPanel.setLayout(new BoxLayout(metricsPanel, BoxLayout.Y_AXIS));
            JTextArea metricsText = new JTextArea(20, 40);
            metricsText.setEditable(false);
            StringBuilder sb = new StringBuilder();
            for (SimulationResult result : results.values()) {
                sb.append("\n").append(result.strategyName).append("\n");
                sb.append("Final Best: ").append(result.finalBestFitness).append("\n");
                sb.append("Final Avg: ").append(String.format("%.2f", result.finalAvgFitness)).append("\n");
                sb.append("Convergence: ").append(String.format("%.2f%%", result.convergenceRate * 100)).append("\n");
                sb.append("Gen to Optimal: ").append(result.generationsToOptimal == -1 ? "Never" : result.generationsToOptimal).append("\n");
                sb.append("Solutions at 10: ").append(result.finalPopulationFitness.stream().filter(f -> f == 10).count()).append("\n");
                sb.append("---\n");
            }
            metricsText.setText(sb.toString());
            metricsPanel.add(new JScrollPane(metricsText));
            frame.add(metricsPanel);

            frame.setSize(1400, 1000);
            frame.setVisible(true);
        });
    }
}