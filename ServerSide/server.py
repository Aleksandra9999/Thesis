from concurrent import futures
import grpc
import data_pb2
import data_pb2_grpc
import time
import threading
import numpy as np
from pyquaternion import Quaternion
import os
import matplotlib.pyplot as plt
from io import BytesIO
from PIL import Image
import subprocess


class Listener(data_pb2_grpc.StreamDataServiceServicer):
    def __init__(self, *args, **kwargs):
        self.counter = 0

    def data(self, request, context):
        #print(request)
        x = request.x
        y = request.y
        z = request.z

        qw = request.qw
        qx = request.qx
        qy = request.qy
        qz = request.qz

        img_bytes = request.image

        stream = BytesIO(img_bytes)
        image = np.array(Image.open(stream).convert("RGBA"))
        stream.close()
        plt.imshow(image)
        plt.show()

        convert = np.array([
            [0, 0, -1, 0],
            [-1, 0, 0, 0],
            [0, 1, 0, 0],
            [0, 0, 0, 1],
        ])

        pos = np.array([
            [1, 0, 0, x],
            [0, 1, 0, y],
            [0, 0, 1, z],
            [0, 0, 0, 1],
        ])

        rot = Quaternion(x=qx, y=qy, z=qz, w=qw).transformation_matrix

        convert2 = np.array([
            [1, 0, 0, 0],
            [0, 1, 0, 0],
            [0, 0, -1, 0],
            [0, 0, 0, 1],
        ])

        mat = convert @ pos @ rot @ convert2

        mat[0, 3] -= 0.0161011 
        mat[1, 3] -= -1.4330566 
        mat[2, 3] -= 0.28272155
        mat[0, 3] += 2
        mat[1, 3] += 2
        mat[2, 3] += 1.5

        print(mat)

        np.savetxt('./data/sample/sample1/pose/' + str(self.counter) + '.txt', mat)
        cv2.write('./data/sample/sample1/color/' + str(self.counter) + 'jpg.', image)
        
        subprocess.run('python /home/onix/Atlas/inference1.py --path data --path_meta data --dataset sample --model results/release/semseg/final.ckpt --scenes data/sample/sample1/info.json --voxel_dim 208 208 80
')

        self.counter += 1

        return data_pb2.Mesh(count = bytes(mat[0][0]))


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=1))
    data_pb2_grpc.add_StreamDataServiceServicer_to_server(Listener(), server)
    server.add_insecure_port("[::]:9999")
    server.start()
    try:
        while(True):
            print("server on: threads %i" %(threading.active_count()))
            time.sleep(10)
    except KeyboardInterrupt:
        print("KeyboardInterrupt")
        server.stop(0)

if __name__ == "__main__":
    print("start")
    serve()
