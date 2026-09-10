package org.ymxs;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * The text form: a {@link Multi} written as JSON, and read back
 * (doc/text.md). It is one way of writing the structure down and not the
 * structure, so nothing here is in the records.
 *
 * <p>A tune is written stream by stream. A register's stream is a list of
 * runs, a run being a stretch of rows that all set it, and the number
 * before a run's values is the rows between the end of the run before it
 * and its own first row. The effects are written the other way, as events
 * at their own row number, since an event is a thing a reader looks for by
 * row and there are few of them.
 *
 * <p>A source is written by its number, 1 upward into the tune's own
 * list, which is the numbering {@link Tune#number} gives. The name beside
 * it is what a writer called it and reaches nothing else.
 *
 * <p>A tune written and read back is the tune it was. The other
 * direction, a text read and written back, is the same text where the
 * text was written by this.
 */
public final class Text {

    /** What the first line names. */
    public static final String FORMAT = "ymxs";

    /** The version of the structure this reads and writes. */
    public static final int VERSION = 1;

    /** The values of a run on one line, before it wraps. */
    private static final int WRAP = 20;

    private Text() {
    }

    // ---------------------------------------------------------------- write

    /** {@code multi} as text. */
    public static String write(Multi multi) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"format\": ").append(Json.quote(FORMAT)).append(",\n");
        out.append("  \"version\": ").append(VERSION).append(",\n");
        out.append("  \"tunes\": [\n");
        for (int at = 0; at < multi.tunes().size(); at++) {
            tune(out, multi.tunes().get(at));
            out.append(at + 1 < multi.tunes().size() ? ",\n" : "\n");
        }
        out.append("  ]\n}\n");
        return out.toString();
    }

    private static void tune(StringBuilder out, Tune tune) {
        out.append("    {\n");
        out.append("      \"title\": ").append(Json.quote(tune.title())).append(",\n");
        out.append("      \"composer\": ").append(Json.quote(tune.composer())).append(",\n");
        out.append("      \"writer\": ").append(Json.quote(tune.writer())).append(",\n");
        out.append("      \"rate\": ").append(tune.rate()).append(",\n");
        out.append("      \"rows\": ").append(tune.table().size()).append(",\n");
        out.append("      \"repeat\": ").append(tune.table().repeat().isPresent()
                ? String.valueOf(tune.table().repeat().getAsInt()) : "null").append(",\n");
        sources(out, tune);
        for (Register register : Register.values()) {
            stream(out, tune, register);
        }
        effects(out, tune);
        out.append("    }");
    }

    private static void sources(StringBuilder out, Tune tune) {
        out.append("      \"sources\": [");
        if (tune.sources().isEmpty()) {
            out.append("],\n");
            return;
        }
        out.append('\n');
        for (int at = 0; at < tune.sources().size(); at++) {
            Source source = tune.sources().get(at);
            out.append("        {\"name\": ").append(Json.quote(source.name()))
                    .append(", \"repeat\": ").append(source.table().repeat().isPresent()
                            ? String.valueOf(source.table().repeat().getAsInt()) : "null")
                    .append(", \"rows\": [");
            List<Integer> values = ((Single) source).values();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                if (i > 0 && i % WRAP == 0) {
                    out.append("\n          ");
                }
                out.append(values.get(i));
            }
            out.append("]}").append(at + 1 < tune.sources().size() ? ",\n" : "\n");
        }
        out.append("      ],\n");
    }

    /** One register's runs, a run a line and a long run wrapped. */
    private static void stream(StringBuilder out, Tune tune, Register register) {
        List<Row> rows = tune.rows();
        List<String> runs = new ArrayList<>();
        int at = 0;
        int end = 0;
        while (at < rows.size()) {
            if (!rows.get(at).registers().containsKey(register)) {
                at++;
                continue;
            }
            int from = at;
            List<Integer> run = new ArrayList<>();
            while (at < rows.size() && rows.get(at).registers().containsKey(register)) {
                run.add(rows.get(at).registers().get(register));
                at++;
            }
            StringBuilder one = new StringBuilder("        [").append(from - end);
            end = at;
            if (run.size() == 1) {
                one.append(", ").append(run.get(0)).append(']');
            } else {
                one.append(", [");
                for (int i = 0; i < run.size(); i++) {
                    if (i > 0) {
                        one.append(',');
                    }
                    if (i > 0 && i % WRAP == 0) {
                        one.append("\n          ");
                    }
                    one.append(run.get(i));
                }
                one.append("]]");
            }
            runs.add(one.toString());
        }
        if (runs.isEmpty()) {
            return;
        }
        out.append("      \"").append(name(register)).append("\": [\n")
                .append(String.join(",\n", runs)).append("\n      ],\n");
    }

    /** The effects as events, each at its own row. */
    private static void effects(StringBuilder out, Tune tune) {
        List<Row> rows = tune.rows();
        List<String> events = new ArrayList<>();
        for (int at = 0; at < rows.size(); at++) {
            Map<Timer, Effect> here = rows.get(at).effects();
            if (here.isEmpty()) {
                continue;
            }
            List<String> said = new ArrayList<>();
            for (Map.Entry<Timer, Effect> one : here.entrySet()) {
                said.add(Json.quote(one.getKey().name()) + ": "
                        + effect(one.getValue(), tune));
            }
            events.add("        [" + at + ", {" + String.join(", ", said) + "}]");
        }
        out.append("      \"effects\": [");
        if (events.isEmpty()) {
            out.append("]\n");
            return;
        }
        out.append('\n').append(String.join(",\n", events)).append("\n      ]\n");
    }

    private static String effect(Effect effect, Tune tune) {
        return switch (effect) {
            case Start start -> "{\"start\": {\"target\": " + Json.quote(start.target().toString())
                    + ", \"source\": " + tune.number(start.source())
                    + ", \"prescaler\": " + start.prescaler().divides()
                    + ", \"count\": " + start.count()
                    + ", \"timerReset\": " + start.timerReset()
                    + ", \"placeReset\": " + start.placeReset() + "}}";
            case Retune retune -> "{\"retune\": {\"prescaler\": " + retune.prescaler().divides()
                    + ", \"count\": " + retune.count()
                    + ", \"timerReset\": " + retune.timerReset()
                    + ", \"placeReset\": " + retune.placeReset() + "}}";
            case Stop ignored -> "{\"stop\": {}}";
        };
    }

    /** A register's name in the text: {@code r0} to {@code r13}. */
    static String name(Register register) {
        return "r" + register.number();
    }

    // ----------------------------------------------------------------- read

    /** The multi {@code text} holds.
     *
     * @throws IllegalArgumentException where the text is not this form, or
     *     states a structure the records do not hold
     */
    @SuppressWarnings("unchecked")
    public static Multi read(String text) {
        Map<String, Object> held = (Map<String, Object>) Json.read(text);
        String format = Json.text(held, "format");
        if (!format.equals(FORMAT)) {
            throw new IllegalArgumentException("a text of " + format + ", and this reads "
                    + FORMAT);
        }
        int version = Json.number(held, "version");
        if (version != VERSION) {
            throw new IllegalArgumentException("version " + version + ", and this reads "
                    + VERSION);
        }
        List<Tune> tunes = new ArrayList<>();
        for (Object one : Json.array(held, "tunes")) {
            tunes.add(tune((Map<String, Object>) one));
        }
        return new Multi(tunes);
    }

    @SuppressWarnings("unchecked")
    private static Tune tune(Map<String, Object> held) {
        int count = Json.number(held, "rows");
        List<Source> sources = new ArrayList<>();
        for (Object one : Json.array(held, "sources")) {
            Map<String, Object> said = (Map<String, Object>) one;
            List<Integer> values = new ArrayList<>();
            for (Object value : Json.array(said, "rows")) {
                values.add(Math.toIntExact((Long) value));
            }
            sources.add(new Single(Json.text(said, "name"),
                    new Table<>(values, maybe(said.get("repeat")))));
        }
        List<Map<Register, Integer>> registers = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            registers.add(new EnumMap<>(Register.class));
        }
        for (Register register : Register.values()) {
            Object runs = held.get(name(register));
            if (runs == null) {
                continue;
            }
            int at = 0;
            for (Object one : (List<Object>) runs) {
                List<Object> run = (List<Object>) one;
                at += Math.toIntExact((Long) run.get(0));
                Object values = run.get(1);
                if (values instanceof List<?> many) {
                    for (Object value : many) {
                        put(registers, count, at++, register, Math.toIntExact((Long) value));
                    }
                } else {
                    put(registers, count, at++, register, Math.toIntExact((Long) values));
                }
            }
        }
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            effects.add(new EnumMap<>(Timer.class));
        }
        for (Object one : Json.array(held, "effects")) {
            List<Object> event = (List<Object>) one;
            int at = Math.toIntExact((Long) event.get(0));
            if (at < 0 || at >= count) {
                throw new IllegalArgumentException("an effect at row " + at + ", and the tune"
                        + " holds " + count + " rows");
            }
            for (Map.Entry<String, Object> said
                    : ((Map<String, Object>) event.get(1)).entrySet()) {
                effects.get(at).put(Timer.valueOf(said.getKey()),
                        effect((Map<String, Object>) said.getValue(), sources, at));
            }
        }
        List<Row> rows = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            rows.add(new Row(registers.get(at), effects.get(at)));
        }
        return new Tune(Json.text(held, "title"), Json.text(held, "composer"),
                Json.text(held, "writer"), Json.number(held, "rate"), sources,
                new Table<>(rows, maybe(held.get("repeat"))));
    }

    private static void put(List<Map<Register, Integer>> rows, int count, int at,
                            Register register, int value) {
        if (at < 0 || at >= count) {
            throw new IllegalArgumentException(name(register) + " sets row " + at + ", and the"
                    + " tune holds " + count + " rows");
        }
        rows.get(at).put(register, value);
    }

    private static Effect effect(Map<String, Object> said, List<Source> sources, int at) {
        if (said.size() != 1) {
            throw new IllegalArgumentException("the effect at row " + at + " states "
                    + said.size() + " things, and it states one of start, retune and stop");
        }
        String shape = said.keySet().iterator().next();
        Map<String, Object> of = Json.object(said, shape);
        return switch (shape) {
            case "start" -> {
                int number = Json.number(of, "source");
                if (number < 1 || number > sources.size()) {
                    throw new IllegalArgumentException("row " + at + " starts source "
                            + number + ", and the tune holds " + sources.size());
                }
                yield new Start(target(Json.text(of, "target")), sources.get(number - 1),
                        Prescaler.dividing(Json.number(of, "prescaler")),
                        Json.number(of, "count"), flag(of, "timerReset"),
                        flag(of, "placeReset"));
            }
            case "retune" -> new Retune(Prescaler.dividing(Json.number(of, "prescaler")),
                    Json.number(of, "count"), flag(of, "timerReset"), flag(of, "placeReset"));
            case "stop" -> Stop.STOP;
            default -> throw new IllegalArgumentException("row " + at + " states \"" + shape
                    + "\" of an effect, and it states one of start, retune and stop");
        };
    }

    /** A target by the name it is written under, {@code setR0} upward. */
    static Target target(String said) {
        for (Register register : Register.values()) {
            Target target = Target.setting(register);
            if (target.toString().equals(said)) {
                return target;
            }
        }
        throw new IllegalArgumentException("no target is called \"" + said + "\"");
    }

    private static boolean flag(Map<String, Object> of, String key) {
        Object value = of.get(key);
        if (value instanceof Boolean said) {
            return said;
        }
        throw new IllegalArgumentException(key + " is " + Json.said(value)
                + ", and true or false is asked");
    }

    /** A repeat row, or none where the text holds null. */
    private static OptionalInt maybe(@Nullable Object value) {
        if (value == Json.NULL) {
            return OptionalInt.empty();
        }
        if (value instanceof Long at) {
            return OptionalInt.of(Math.toIntExact(at));
        }
        throw new IllegalArgumentException("repeat is " + Json.said(value)
                + ", and a row number or null is asked");
    }
}
