
package org.teavm.gdx.plugin;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.Reader;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.teavm.common.Mapper;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.gdx.emu.BufferUtilsEmulator;
import org.teavm.gdx.emu.IndexArrayEmulator;
import org.teavm.gdx.emu.Matrix4Emulator;
import org.teavm.gdx.emu.PixmapEmulator;
import org.teavm.gdx.emu.TextureDataEmulator;
import org.teavm.gdx.emu.VertexArrayEmulator;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.util.ModelUtils;
import org.teavm.parsing.ClassRefsRenamer;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.glutils.IndexArray;
import com.badlogic.gdx.graphics.glutils.IndexBufferObject;
import com.badlogic.gdx.graphics.glutils.VertexArray;
import com.badlogic.gdx.graphics.glutils.VertexBufferObject;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;

public class OverlayTransformer implements ClassHolderTransformer {
	@Override
	public void transformClass (final ClassHolder cls, final ClassReaderSource innerSource, final Diagnostics diagnostics) {
		if (cls.getName().equals(BufferUtils.class.getName())) {
			transformBufferUtils(cls, innerSource);
		} else if (cls.getName().equals(TextureData.Factory.class.getName())) {
			transformTextureData(cls, innerSource);
		} else if (cls.getName().equals(FileHandle.class.getName())) {
			transformFileHandle(cls);
		} else if (cls.getName().equals(Pixmap.class.getName())) {
			replaceClass(cls, innerSource.get(PixmapEmulator.class.getName()));
		} else if (cls.getName().equals(Matrix4.class.getName())) {
			transformMatrix(cls, innerSource);
		} else
			if (cls.getName().equals(VertexArray.class.getName()) || cls.getName().equals(VertexBufferObject.class.getName())) {
			replaceClass(cls, innerSource.get(VertexArrayEmulator.class.getName()));
		} else if (cls.getName().equals(IndexArray.class.getName()) || cls.getName().equals(IndexBufferObject.class.getName())) {
			replaceClass(cls, innerSource.get(IndexArrayEmulator.class.getName()));
		}
	}

	private static void transformBufferUtils (final ClassHolder cls, final ClassReaderSource innerSource) {
		final List<MethodDescriptor> descList = new ArrayList<>();
		descList.add(new MethodDescriptor("freeMemory", ByteBuffer.class, void.class));
		descList.add(new MethodDescriptor("newDisposableByteBuffer", int.class, ByteBuffer.class));
		descList.add(new MethodDescriptor("copyJni", float[].class, Buffer.class, int.class, int.class, void.class));
		replaceMethods(cls, BufferUtilsEmulator.class, innerSource, descList);
	}

	private static void transformMatrix (final ClassHolder cls, final ClassReaderSource innerSource) {
		final List<MethodDescriptor> descList = new ArrayList<>();
		descList.add(new MethodDescriptor("inv", float[].class, boolean.class));
		descList.add(new MethodDescriptor("mul", float[].class, float[].class, void.class));
		descList.add(new MethodDescriptor("prj", float[].class, float[].class, int.class, int.class, int.class, void.class));
		replaceMethods(cls, Matrix4Emulator.class, innerSource, descList);
		final ClassReader emuClass = innerSource.get(Matrix4Emulator.class.getName());
		cls.addMethod(ModelUtils.copyMethod(emuClass.getMethod(new MethodDescriptor("matrix4_det", float[].class, float.class))));
		cls.addMethod(ModelUtils.copyMethod(
			emuClass.getMethod(new MethodDescriptor("matrix4_proj", float[].class, float[].class, int.class, void.class))));
	}

	private static void transformTextureData (final ClassHolder cls, final ClassReaderSource innerSource) {
		final List<MethodDescriptor> descList = new ArrayList<>();
		descList.add(new MethodDescriptor("loadFromFile", FileHandle.class, Format.class, boolean.class, TextureData.class));
		replaceMethods(cls, TextureDataEmulator.class, innerSource, descList);
	}

	private static void transformFileHandle (final ClassHolder cls) {
		final Set<MethodDescriptor> methodsToRetain = new HashSet<>();
		final Set<MethodDescriptor> methodsToRetainUnmodified = new HashSet<>();
		methodsToRetain.add(new MethodDescriptor("<init>", void.class));
		methodsToRetain.add(new MethodDescriptor("<init>", String.class, void.class));
		methodsToRetain.add(new MethodDescriptor("<init>", String.class, FileType.class, void.class));
		methodsToRetain.add(new MethodDescriptor("path", String.class));
		methodsToRetain.add(new MethodDescriptor("name", String.class));
		methodsToRetain.add(new MethodDescriptor("extension", String.class));
		methodsToRetain.add(new MethodDescriptor("nameWithoutExtension", String.class));
		methodsToRetain.add(new MethodDescriptor("pathWithoutExtension", String.class));
		methodsToRetain.add(new MethodDescriptor("type", FileType.class));
		methodsToRetain.add(new MethodDescriptor("read", InputStream.class));
		methodsToRetainUnmodified.add(new MethodDescriptor("read", int.class, BufferedInputStream.class));
		methodsToRetainUnmodified.add(new MethodDescriptor("reader", Reader.class));
		methodsToRetainUnmodified.add(new MethodDescriptor("reader", int.class, BufferedReader.class));
		methodsToRetain.add(new MethodDescriptor("readString", String.class));
		methodsToRetain.add(new MethodDescriptor("readBytes", byte[].class));
		methodsToRetain.add(new MethodDescriptor("estimateLength", int.class));
		methodsToRetain.add(new MethodDescriptor("readBytes", int[].class, int.class, int.class, int.class));
		methodsToRetain.add(new MethodDescriptor("list", FileHandle[].class));
		methodsToRetain.add(new MethodDescriptor("list", String.class, FileHandle[].class));
		methodsToRetain.add(new MethodDescriptor("isDirectory", boolean.class));
		methodsToRetain.add(new MethodDescriptor("child", String.class, FileHandle.class));
		methodsToRetain.add(new MethodDescriptor("sibling", String.class, FileHandle.class));
		methodsToRetain.add(new MethodDescriptor("parent", FileHandle.class));
		methodsToRetain.add(new MethodDescriptor("exists", boolean.class));
		methodsToRetain.add(new MethodDescriptor("length", long.class));
		methodsToRetain.add(new MethodDescriptor("lastModified", long.class));
		methodsToRetainUnmodified.add(new MethodDescriptor("equals", Object.class, boolean.class));
		methodsToRetainUnmodified.add(new MethodDescriptor("hashCode", int.class));
		for (final MethodHolder method : cls.getMethods().toArray(new MethodHolder[0])) {
			if (methodsToRetain.contains(method.getDescriptor())) {
			if (method.getName().equals("<init>")) {
				method.setProgram(createInitStubProgram());
			} else {
				method.setProgram(createStubProgram());
			}
			} else if (!methodsToRetainUnmodified.contains(method.getDescriptor())) {
			cls.removeMethod(method);
			}
		}
		for (final FieldHolder field : cls.getFields().toArray(new FieldHolder[0])) {
			cls.removeField(field);
		}
	}

	private static Program createStubProgram () {
		final Program program = new Program();
		program.createVariable(); // this
		final BasicBlock block = program.createBasicBlock();
		final Variable ex = program.createVariable();
		final ConstructInstruction consInsn = new ConstructInstruction();
		consInsn.setReceiver(ex);
		consInsn.setType(UnsupportedOperationException.class.getName());
		block.getInstructions().add(consInsn);
		final InvokeInstruction initInsn = new InvokeInstruction();
		initInsn.setType(InvocationType.SPECIAL);
		initInsn.setInstance(ex);
		initInsn.setMethod(new MethodReference(UnsupportedOperationException.class, "<init>", void.class));
		block.getInstructions().add(initInsn);
		final RaiseInstruction raiseInsn = new RaiseInstruction();
		raiseInsn.setException(ex);
		block.getInstructions().add(raiseInsn);
		return program;
	}

	private static Program createInitStubProgram () {
		final Program program = new Program();
		final BasicBlock block = program.createBasicBlock();
		final Variable self = program.createVariable();
		final InvokeInstruction superInitInsn = new InvokeInstruction();
		superInitInsn.setType(InvocationType.SPECIAL);
		superInitInsn.setInstance(self);
		superInitInsn.setMethod(new MethodReference(Object.class, "<init>", void.class));
		block.getInstructions().add(superInitInsn);
		final ExitInstruction exitInsn = new ExitInstruction();
		block.getInstructions().add(exitInsn);
		return program;
	}

	private static void replaceMethods (final ClassHolder cls, final Class<?> emuType, final ClassReaderSource innerSource,
		final List<MethodDescriptor> descList) {
		final ClassReader emuCls = innerSource.get(emuType.getName());
		for (final MethodDescriptor methodDesc : descList) {
			cls.removeMethod(cls.getMethod(methodDesc));
			cls.addMethod(ModelUtils.copyMethod(emuCls.getMethod(methodDesc)));
		}
	}

	private static void replaceClass (final ClassHolder cls, final ClassReader emuCls) {
		final ClassRefsRenamer renamer = new ClassRefsRenamer(new Mapper<String, String>() {
			@Override
			public String map (final String preimage) {
			return preimage.equals(emuCls.getName()) ? cls.getName() : preimage;
			}
		});
		for (final FieldHolder field : cls.getFields().toArray(new FieldHolder[0])) {
			cls.removeField(field);
		}
		for (final MethodHolder method : cls.getMethods().toArray(new MethodHolder[0])) {
			cls.removeMethod(method);
		}
		for (final FieldReader field : emuCls.getFields()) {
			cls.addField(ModelUtils.copyField(field));
		}
		for (final MethodReader method : emuCls.getMethods()) {
			cls.addMethod(renamer.rename(ModelUtils.copyMethod(method)));
		}
	}
}
