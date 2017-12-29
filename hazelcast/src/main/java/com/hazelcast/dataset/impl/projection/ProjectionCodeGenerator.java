package com.hazelcast.dataset.impl.projection;

import com.hazelcast.dataset.ProjectionRecipe;
import com.hazelcast.dataset.impl.RecordMetadata;
import com.hazelcast.dataset.impl.ScanCodeGenerator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public class ProjectionCodeGenerator extends ScanCodeGenerator {

    private final ProjectionRecipe projectionRecipe;
    private final Class<?> projectionClass;

    public ProjectionCodeGenerator(String compilationId,
                                   ProjectionRecipe projectionRecipe,
                                   RecordMetadata recordMetadata) {
        super(compilationId, projectionRecipe.getPredicate(), recordMetadata);

        this.projectionRecipe = projectionRecipe;
        try {
            this.projectionClass = ProjectionCodeGenerator.class.getClassLoader().loadClass(projectionRecipe.getClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String className() {
        return "ProjectionScan_" + compilationId;
    }

    @Override
    public void generate() {
        append("import java.util.*;\n");
        append("public class " + className() + " extends com.hazelcast.dataset.impl.projection.ProjectionScan {\n\n");

        generateRunMethod();
        generateBindFields();
        generateBindMethod();

        append("}\n");
    }

    private void generateRunMethod() {
        append("    public void run(){\n");
        append("       long offset = slabPointer;\n");
        append("       " + projectionClass.getName() + " object = new " + projectionClass.getName() + "();\n");
        append("       for(long l=0; l<recordIndex; l++){\n");
        append("           if(");
        toCode(predicate);
        append("){\n");

        for (Field field : extractedFields()) {
            append("               object.").append(field.getName()).append(" = ");
            generateGetField(field.getName());
            append(";\n");
        }
        append("               consumer.accept(object);\n");
        append("           }\n");
        append("           offset += recordDataSize;\n");
        append("        }\n");
        append("    }\n\n");
    }

    private Set<Field> extractedFields() {
        Set<Field> fields = new HashSet<Field>();

        for (Field f : projectionClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }

            Field recordField = recordMetadata.getField(f.getName());
            if (recordField == null) {
                throw new RuntimeException(
                        "Field '" + projectionClass.getName() + '.' + f.getName()
                                + "' is not found on value-class '" + recordMetadata.getValueClass() + "'");
            }

            if (!recordField.getType().equals(f.getType())) {
                throw new RuntimeException(
                        "Field '" + projectionClass.getName() + '.' + f.getName()
                                + "' has a different type compared to '" + recordMetadata.getValueClass() + "'");
            }

            fields.add(f);
        }

        return fields;
    }
}
