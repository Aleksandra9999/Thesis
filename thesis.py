import numpy
import trimesh
import pyrender

mesh = trimesh.load('sample1.ply', process=False)
print(mesh)
mesh.show()
pyrender.Mesh.from_trimesh(mesh)