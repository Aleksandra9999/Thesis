# Copyright 2020 Magic Leap, Inc.

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at

#     http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#  Originating Author: Zak Murez (zak.murez.com)

import argparse
import os

import numpy as np
import torch

from atlas.data import SceneDataset, parse_splits_list
from atlas.model import VoxelNet
import atlas.transforms as transforms
import trimesh
import matplotlib.pyplot as plt
import tempfile
#from PIL import get_mesh
from pyglet import gl
import time
import threading

from threading import Thread
import random


my_mesh = trimesh.primitives.Sphere()
my_mesh.visual.face_colors = [0, 0, 0, 0]
translation = [7, 5, 6]  
my_mesh.apply_translation(translation)


 
class MyThread1(Thread):
    
    def __init__(self, name):

        Thread.__init__(self)
        self.name = name
        self.scene = trimesh.Scene()
        self.scene.add_geometry(my_mesh, geom_name="mesh")
    
    def update(self, scene):
        scene.delete_geometry("mesh")
        scene.add_geometry(my_mesh,  geom_name="mesh")
       
    
    def run(self):
        msg = "%s is running" % self.name
        print(msg)
        self.scene.show(callback=self.update)


def process(info_file, model, num_frames, save_path, total_scenes_index, total_scenes_count, window_conf):
    """ Run the netork on a scene and save output

    Args:
        info_file: path to info_json file for the scene
        model: pytorch model that implemets Atlas
        frames: number of frames to use in reconstruction (-1 for all)
        save_path: where to save outputs
        total_scenes_index: used to print which scene we are on
        total_scenes_count: used to print the total number of scenes to process
    """
    global my_mesh
    voxel_scale = model.voxel_sizes[0]
    dataset = SceneDataset(info_file, voxel_sizes=[voxel_scale],
                           voxel_types=model.voxel_types, num_frames=num_frames)

    # compute voxel origin
    if 'file_name_vol_%02d'%voxel_scale in dataset.info:
        # compute voxel origin from ground truth
        tsdf_trgt = dataset.get_tsdf()['vol_%02d'%voxel_scale]
        voxel_size = float(voxel_scale)/100
        # shift by integer number of voxels for padding
        shift = torch.tensor([.5, .5, .5])//voxel_size
        offset = tsdf_trgt.origin - shift*voxel_size

    else:
        # use default origin
        # assume floor is a z=0 so pad bottom a bit
        offset = torch.tensor([0,0,-.5])

    T = torch.eye(4)
    T[:3,3] = offset

    transform = transforms.Compose([
        transforms.ResizeImage((640,480)),
        transforms.ToTensor(),
        transforms.TransformSpace(T, model.voxel_dim_val, [0,0,0]),
        transforms.IntrinsicsPoseToProjection(),
    ])
    dataset.transform = transform
    dataloader = torch.utils.data.DataLoader(dataset, batch_size=None,
                                             batch_sampler=None, num_workers=2)

    scene = dataset.info['scene']

    model.initialize_volume()
    torch.cuda.empty_cache()

    start = time.time()

    for j, d in enumerate(dataloader):
    
        # logging progress
        if j%25==0:
            print(time.time()-start)
            start = time.time()
            print(total_scenes_index,
                  total_scenes_count,
                  dataset.info['dataset'],
                  scene,
                  j,
                  len(dataloader)
            )
            

        model.inference1(d['projection'].unsqueeze(0).cuda(),
                            image=d['image'].unsqueeze(0).cuda())

        outputs, losses = model.inference2()

        tsdf_pred = model.postprocess(outputs)[0]

        # TODO: set origin in model... make consistent with offset above?
        tsdf_pred.origin = offset.view(1,3).cuda()


        if 'semseg' in tsdf_pred.attribute_vols:
            mesh_pred = tsdf_pred.get_mesh('semseg')
            # save vertex attributes seperately since trimesh doesn't
            np.savez(os.path.join(save_path, '%s_attributes.npz'%scene), 
                    **mesh_pred.vertex_attributes)
        else:
            mesh_pred = tsdf_pred.get_mesh()

        tsdf_pred.save(os.path.join(save_path, '%s.npz'%scene))
        my_mesh = mesh_pred
    #mesh_pred.show()
    tsdf_pred.save(os.path.join(save_path, '%s.npz'%scene))
    mesh_pred.export(os.path.join(save_path, '%s.ply'%scene))
    trimesh.exchange.gltf.export_glb(scene, extras=None, include_normals=None, tree_postprocessor=None)
    


import json
import os

import numpy as np


def prepare_sample_scene(scene, path, path_meta, verbose=2):

    """    
    JSON format:
        {'dataset': 'sample',
         'path': path,
         'scene': scene,
         'frames': [{'file_name_image': '',
                     'intrinsics': intrinsics,
                     'pose': pose,
                     }
                   ]
         }

    """

    if verbose>0:
        print('preparing %s'%scene)
    
    intrinsics = np.loadtxt(os.path.join(path, scene, 'intrinsics.txt'))

    frame_ids = os.listdir(os.path.join(path, scene, 'color'))
    frame_ids = [int(os.path.splitext(frame)[0]) for frame in frame_ids]
    frame_ids =  sorted(frame_ids)
    
    str_to_txt = ""

    os.makedirs(os.path.join(path_meta, scene), exist_ok=True)

    for i, frame_id in enumerate(frame_ids):
        if verbose>1 and i%25==0:
            print('preparing %s frame %d/%d'%(scene, i, len(frame_ids)))

        data = {'dataset': 'sample',
        'path': path,
        'scene': scene,
        'frames': []
        }

        pose = np.loadtxt(os.path.join(path, scene, 'pose', '%d.txt' % frame_id))

        # skip frames with no valid pose
        if not np.all(np.isfinite(pose)):
            continue

        frame = {'file_name_image': 
                     os.path.join(path, scene, 'color', '%d.jpg'%frame_id),
                 'intrinsics': intrinsics.tolist(),
                 'pose': pose.tolist(),
                }
        data['frames'].append(frame)
    
        json.dump(data, open(os.path.join(path_meta, scene, 'info' + '.json'), 'w')) #+ str(i)
        str_to_txt += str(os.path.join(path_meta, scene, 'info' + '.json\n')) #+ str(i)

    f = open(os.path.join(path_meta, scene, 'info.txt'), 'w')
    f.write(str_to_txt)




def main():

    parser = argparse.ArgumentParser(description='Fuse ground truth tsdf on Scannet')
    parser.add_argument("--path", required=True, metavar="DIR",
        help="path to raw dataset")
    parser.add_argument("--path_meta", required=True, metavar="DIR",
        help="path to store processed (derived) dataset")
    parser.add_argument("--dataset", required=True, type=str,
        help="which dataset to prepare")
    parser.add_argument('--i', default=0, type=int,
        help='index of part for parallel processing')
    parser.add_argument('--n', default=1, type=int,
        help='number of parts to devide data into for parallel processing')
    parser.add_argument('--test', action='store_true',
        help='only prepare the test set (for rapid testing if you dont plan to train)')
    parser.add_argument('--max_depth', default=3., type=float,
        help='mask out large depth values since they are noisy')

    parser.add_argument("--model", required=True, metavar="FILE",
                        help="path to checkpoint")
    parser.add_argument("--scenes", default="data/scannet_test.txt",
                        help="which scene(s) to run on")
    parser.add_argument("--num_frames", default=-1, type=int,
                        help="number of frames to use (-1 for all)")
    parser.add_argument("--voxel_dim", nargs=3, default=[-1,-1,-1], type=int,
                        help="override voxel dim")
    args = parser.parse_args()

    i=args.i
    n=args.n
    assert 0<=i and i<n
    
    if args.dataset == 'sample':
        scenes = ['sample1']
        scenes = scenes[i::n] # distribute among workers
        for scene in scenes:
            prepare_sample_scene(
                scene,
                os.path.join(args.path, 'sample'),
                os.path.join(args.path_meta, 'sample'),
            )

    elif args.dataset == 'scannet':
        prepare_scannet(
            os.path.join(args.path, 'scannet'),
            os.path.join(args.path_meta, 'scannet'),
            i,
            n,
            args.test,
            args.max_depth
        )

    else:
        raise NotImplementedError('unknown dataset %s'%args.dataset)

    
    name = "Thread #%s" % (2)
    thread1 = MyThread1(name)
    thread1.start()

    # get all the info_file.json's from the command line
    # .txt files contain a list of info_file.json's
    info_files = parse_splits_list(args.scenes)

    model = VoxelNet.load_from_checkpoint(args.model)
    model = model.cuda().eval()
    torch.set_grad_enabled(False)

    # overwrite default values of voxel_dim_test
    if args.voxel_dim[0] != -1:
        model.voxel_dim_test = args.voxel_dim
    # TODO: implement voxel_dim_test
    model.voxel_dim_val = model.voxel_dim_test

    model_name = os.path.splitext(os.path.split(args.model)[1])[0]
    save_path = os.path.join(model.cfg.LOG_DIR, model.cfg.TRAINER.NAME,
                             model.cfg.TRAINER.VERSION, 'test_'+model_name)
    if args.num_frames>-1:
        save_path = '%s_%d'%(save_path, args.num_frames)
    os.makedirs(save_path, exist_ok=True)
    
    i = 0
    # run model on each scene
    for info_file in info_files:
        window_conf = gl.Config(double_buffer=True, depth_size=24)
        process(info_file, model, args.num_frames, save_path, i, len(info_files), window_conf)
        #i+=1


if __name__ == "__main__":
    main()
