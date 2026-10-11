import { Test, TestingModule } from '@nestjs/testing';
import { AppleTvController } from './apple-tv.controller';

describe('AppleTvController', () => {
  let controller: AppleTvController;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AppleTvController],
    }).compile();

    controller = module.get<AppleTvController>(AppleTvController);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });
});
